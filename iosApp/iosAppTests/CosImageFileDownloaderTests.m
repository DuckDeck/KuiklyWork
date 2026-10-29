#import <XCTest/XCTest.h>
#import "CosImageFileDownloader.h"

@interface CosImageFileDownloaderTests : XCTestCase @end
@implementation CosImageFileDownloaderTests
- (void)testPublishedFileIsRecoveredWithoutAnotherNetworkRequest {
    NSString *identifier = NSUUID.UUID.UUIDString;
    NSURL *documents = [NSFileManager.defaultManager URLsForDirectory:NSDocumentDirectory inDomains:NSUserDomainMask].firstObject;
    NSURL *directory = [documents URLByAppendingPathComponent:[@"CosDownloads/" stringByAppendingString:identifier]];
    NSURL *target = [directory URLByAppendingPathComponent:@"0.gif"];
    NSError *error = nil;
    XCTAssertTrue([NSFileManager.defaultManager createDirectoryAtURL:directory withIntermediateDirectories:YES attributes:nil error:&error]);
    NSData *published = [@"GIF89a-published-test-fixture" dataUsingEncoding:NSUTF8StringEncoding];
    XCTAssertTrue([published writeToURL:target options:NSDataWritingAtomic error:&error]);
    @try {
        // .invalid cannot be downloaded. Recovery must return before any HTTP request.
        NSDictionary *task = @{@"taskId":identifier, @"imageUrls":@[@"https://image.invalid/0.gif"], @"referer":@"https://www.nncos.com/"};
        __block NSString *checkpointed;
        NSString *saved = [[CosImageFileDownloader new] download:task index:0 checkpoint:^BOOL(NSString *location, NSError **writeError) {
            checkpointed = location; return YES;
        } error:&error];
        XCTAssertNil(error);
        XCTAssertEqualObjects(saved, target.path);
        XCTAssertEqualObjects(checkpointed, target.path);
        XCTAssertEqualObjects([NSData dataWithContentsOfURL:target], published);
    } @finally {
        [NSFileManager.defaultManager removeItemAtURL:directory error:nil];
    }
}
@end
