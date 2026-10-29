#import <XCTest/XCTest.h>
#import "CosDownloadTaskStore.h"
@interface CosDownloadTaskStoreTests : XCTestCase @end
@implementation CosDownloadTaskStoreTests
- (void)testAtomicRoundTripAndBackup {
    NSURL *dir = [NSURL fileURLWithPath:[NSTemporaryDirectory() stringByAppendingPathComponent:NSUUID.UUID.UUIDString]];
    NSURL *file = [dir URLByAppendingPathComponent:@"tasks.json"];
    CosDownloadTaskStore *store = [[CosDownloadTaskStore alloc] initWithURL:file];
    NSDictionary *task = @{@"taskId":@"id", @"detailUrl":@"https://www.nncos.com/1.html", @"title":@"一组", @"imageUrls":@[@"a"], @"referer":@"", @"completed":@0, @"status":@"running", @"targets":@{}, @"message":@""};
    NSError *error = nil;
    XCTAssertTrue([store save:@[task] error:&error]);
    XCTAssertEqualObjects([store load:&error], (@[task]));
    XCTAssertTrue([store save:@[] error:&error]);
    [@"broken" writeToURL:file atomically:YES encoding:NSUTF8StringEncoding error:nil];
    XCTAssertEqualObjects([store load:&error], (@[task]));
    [NSFileManager.defaultManager removeItemAtURL:dir error:nil];
}
@end
