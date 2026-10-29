#import <XCTest/XCTest.h>
#import "CosDownloadTaskManager.h"
#import "CosDownloadTaskStore.h"

@interface CosMemoryStore : CosDownloadTaskStore
@property NSArray *records;
@property NSInteger failCompleted;
@end
@implementation CosMemoryStore
- (instancetype)init { if ((self = [super init])) { _records = @[]; _failCompleted = -1; } return self; }
- (NSArray *)load:(NSError **)error { return self.records; }
- (BOOL)save:(NSArray *)records error:(NSError **)error {
    for (NSDictionary *record in records) if ([record[@"completed"] integerValue] == self.failCompleted) {
        if (error) *error = [NSError errorWithDomain:@"test" code:1 userInfo:@{NSLocalizedDescriptionKey:@"disk failure"}];
        return NO;
    }
    self.records = [records copy]; return YES;
}
@end

@interface CosDownloadTaskTests : XCTestCase @end
@implementation CosDownloadTaskTests
- (NSDictionary *)request {
    NSMutableArray *urls = [NSMutableArray array];
    for (NSInteger i = 0; i < 33; i++) [urls addObject:[NSString stringWithFormat:@"https://img.nncos.com/%ld.webp", (long)i]];
    return @{@"detailUrl":@"https://www.nncos.com/28131.html", @"title":@"一组", @"imageUrls":urls, @"referer":@"https://www.nncos.com/"};
}
- (NSDictionary *)start:(CosDownloadTaskManager *)manager request:(NSDictionary *)request {
    XCTestExpectation *done = [self expectationWithDescription:@"start reply"];
    __block NSDictionary *reply;
    [manager startWithRequest:request completion:^(NSDictionary *value) { reply = value; [done fulfill]; }];
    [self waitForExpectations:@[done] timeout:3]; return reply;
}
- (void)waitUntilStopped:(CosDownloadTaskManager *)manager {
    XCTestExpectation *done = [self expectationWithDescription:@"task stops"];
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_DEFAULT, 0), ^{
        for (NSInteger i = 0; i < 200; i++) {
            if (![[manager snapshotForDetailURL:nil][@"status"] isEqual:@"running"]) break;
            [NSThread sleepForTimeInterval:0.01];
        }
        [done fulfill];
    });
    [self waitForExpectations:@[done] timeout:4];
    XCTAssertNotEqualObjects([manager snapshotForDetailURL:nil][@"status"], @"running");
}
- (void)testResumeOnlyDownloadsRemainingImages {
    CosMemoryStore *store = [CosMemoryStore new];
    CosTaskDownloadBlock download = ^NSString *(NSDictionary *task, NSUInteger index, CosTargetCheckpoint checkpoint, NSError **error) {
        if (index == 10) { if (error) *error = [NSError errorWithDomain:@"test" code:1 userInfo:nil]; return nil; }
        NSString *target = [NSString stringWithFormat:@"%@/%lu", task[@"taskId"], (unsigned long)index];
        return checkpoint(target, error) ? target : nil;
    };
    CosDownloadTaskManager *first = [[CosDownloadTaskManager alloc] initWithStore:store downloader:download recorder:^BOOL(NSString *url, NSError **error) { return YES; }];
    [self start:first request:self.request]; [self waitUntilStopped:first];
    XCTAssertEqualObjects([first snapshotForDetailURL:nil][@"completed"], @10);
    __block NSUInteger saved = 0;
    CosDownloadTaskManager *restored = [[CosDownloadTaskManager alloc] initWithStore:store downloader:^NSString *(NSDictionary *task, NSUInteger index, CosTargetCheckpoint checkpoint, NSError **error) {
        saved++; XCTAssertGreaterThanOrEqual(index, 10u);
        NSString *target = [NSString stringWithFormat:@"%@/%lu", task[@"taskId"], (unsigned long)index];
        return checkpoint(target, error) ? target : nil;
    } recorder:^BOOL(NSString *url, NSError **error) { return YES; }];
    XCTAssertEqual(saved, 0u);
    XCTAssertEqualObjects([restored snapshotForDetailURL:nil][@"status"], @"interrupted");
    [self start:restored request:self.request]; [self waitUntilStopped:restored];
    XCTAssertEqual(saved, 23u);
    XCTAssertEqualObjects([restored snapshotForDetailURL:nil][@"completed"], @33);
    XCTAssertEqualObjects([restored snapshotForDetailURL:nil][@"status"], @"completed");
}
- (void)testSingleFlightAndFinalizationRetry {
    CosMemoryStore *store = [CosMemoryStore new];
    dispatch_semaphore_t gate = dispatch_semaphore_create(0);
    __block NSUInteger saved = 0;
    __block BOOL failRecord = YES;
    CosDownloadTaskManager *manager = [[CosDownloadTaskManager alloc] initWithStore:store downloader:^NSString *(NSDictionary *task, NSUInteger index, CosTargetCheckpoint checkpoint, NSError **error) {
        saved++; dispatch_semaphore_wait(gate, dispatch_time(DISPATCH_TIME_NOW, NSEC_PER_SEC * 3)); return @"target";
    } recorder:^BOOL(NSString *url, NSError **error) { return !failRecord; }];
    NSMutableDictionary *req = [self.request mutableCopy]; req[@"imageUrls"] = @[req[@"imageUrls"][0]];
    NSDictionary *a = [self start:manager request:req];
    XCTAssertEqualObjects([self start:manager request:req][@"task"][@"taskId"], a[@"task"][@"taskId"]);
    NSMutableDictionary *other = [req mutableCopy]; other[@"detailUrl"] = @"https://www.nncos.com/2.html";
    XCTAssertEqualObjects([self start:manager request:other][@"code"], @1);
    dispatch_semaphore_signal(gate); [self waitUntilStopped:manager];
    XCTAssertEqualObjects([manager snapshotForDetailURL:nil][@"status"], @"interrupted");
    failRecord = NO;
    [self start:manager request:req]; [self waitUntilStopped:manager];
    XCTAssertEqual(saved, 1u);
    XCTAssertEqualObjects([manager snapshotForDetailURL:nil][@"status"], @"completed");
}
- (void)testReusesTargetAfterCrashBeforeCheckpoint {
    CosMemoryStore *store = [CosMemoryStore new];
    NSMutableSet *targets = [NSMutableSet set];
    CosDownloadTaskManager *first = [[CosDownloadTaskManager alloc] initWithStore:store downloader:^NSString *(NSDictionary *task, NSUInteger index, CosTargetCheckpoint checkpoint, NSError **error) {
        NSString *target = [task[@"taskId"] stringByAppendingString:@"/0"];
        if (!checkpoint(target, error)) return nil;
        [targets addObject:target]; store.failCompleted = 1; return target;
    } recorder:^BOOL(NSString *url, NSError **error) { return YES; }];
    NSMutableDictionary *req = [self.request mutableCopy]; req[@"imageUrls"] = @[req[@"imageUrls"][0]];
    [self start:first request:req]; [self waitUntilStopped:first];
    store.failCompleted = -1;
    CosDownloadTaskManager *restored = [[CosDownloadTaskManager alloc] initWithStore:store downloader:^NSString *(NSDictionary *task, NSUInteger index, CosTargetCheckpoint checkpoint, NSError **error) {
        NSString *target = task[@"targets"][@"0"] ?: @"unexpected";
        if (!checkpoint(target, error)) return nil;
        [targets addObject:target]; return target;
    } recorder:^BOOL(NSString *url, NSError **error) { return YES; }];
    [self start:restored request:req]; [self waitUntilStopped:restored];
    XCTAssertEqual(targets.count, 1u);
    XCTAssertFalse([targets containsObject:@"unexpected"]);
    XCTAssertEqualObjects([restored snapshotForDetailURL:nil][@"status"], @"completed");
}
- (void)testFailedReplacementPreservesCompletedRecord {
    CosMemoryStore *store = [CosMemoryStore new];
    __block NSUInteger saved = 0;
    CosDownloadTaskManager *manager = [[CosDownloadTaskManager alloc] initWithStore:store downloader:^NSString *(NSDictionary *task, NSUInteger index, CosTargetCheckpoint checkpoint, NSError **error) {
        saved++; return @"target";
    } recorder:^BOOL(NSString *url, NSError **error) { return YES; }];
    NSMutableDictionary *req = [self.request mutableCopy]; req[@"imageUrls"] = @[req[@"imageUrls"][0]];
    NSString *firstID = [self start:manager request:req][@"task"][@"taskId"];
    [self waitUntilStopped:manager];
    store.failCompleted = 0;
    XCTAssertEqualObjects([self start:manager request:req][@"code"], @(-1));
    XCTAssertEqualObjects([manager snapshotForDetailURL:nil][@"status"], @"completed");
    store.failCompleted = -1;
    XCTAssertNotEqualObjects([self start:manager request:req][@"task"][@"taskId"], firstID);
    [self waitUntilStopped:manager]; XCTAssertEqual(saved, 2u);
}
- (void)testFailedFinalizationDoesNotPublishNewBadgeAfterRestart {
    CosMemoryStore *store = [CosMemoryStore new];
    CosDownloadTaskManager *manager = [[CosDownloadTaskManager alloc] initWithStore:store downloader:^NSString *(NSDictionary *task, NSUInteger index, CosTargetCheckpoint checkpoint, NSError **error) {
        return @"target";
    } recorder:^BOOL(NSString *url, NSError **error) { return NO; }];
    NSMutableDictionary *req = [self.request mutableCopy]; req[@"imageUrls"] = @[req[@"imageUrls"][0]];
    NSArray *rawRecords = @[req[@"detailUrl"]];
    [self start:manager request:req]; [self waitUntilStopped:manager];
    XCTAssertEqual([manager visibleDownloadedURLs:rawRecords].count, 0u);
    CosDownloadTaskManager *restored = [[CosDownloadTaskManager alloc] initWithStore:store downloader:^NSString *(NSDictionary *task, NSUInteger index, CosTargetCheckpoint checkpoint, NSError **error) {
        XCTFail(@"Images must not download again during finalization"); return nil;
    } recorder:^BOOL(NSString *url, NSError **error) { return YES; }];
    XCTAssertEqual([restored visibleDownloadedURLs:rawRecords].count, 0u);
    [self start:restored request:req]; [self waitUntilStopped:restored];
    XCTAssertEqualObjects([restored visibleDownloadedURLs:rawRecords], rawRecords);
}
- (void)testPreviouslyDownloadedBadgeSurvivesFailedRedownload {
    CosMemoryStore *store = [CosMemoryStore new];
    CosDownloadTaskManager *manager = [[CosDownloadTaskManager alloc] initWithStore:store downloader:^NSString *(NSDictionary *task, NSUInteger index, CosTargetCheckpoint checkpoint, NSError **error) {
        return nil;
    } recorder:^BOOL(NSString *url, NSError **error) { return YES; } previouslyDownloaded:^BOOL(NSString *url) { return YES; }];
    [self start:manager request:self.request]; [self waitUntilStopped:manager];
    NSArray *urls = @[self.request[@"detailUrl"]];
    XCTAssertEqualObjects([manager visibleDownloadedURLs:urls], urls);
    CosDownloadTaskManager *restored = [[CosDownloadTaskManager alloc] initWithStore:store downloader:^NSString *(NSDictionary *task, NSUInteger index, CosTargetCheckpoint checkpoint, NSError **error) {
        XCTFail(@"Must not auto-start"); return nil;
    } recorder:^BOOL(NSString *url, NSError **error) { return YES; }];
    XCTAssertEqualObjects([restored visibleDownloadedURLs:urls], urls);
}
@end
