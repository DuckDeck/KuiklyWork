#import "CosImageFileDownloader.h"
#import "HRBridgeModule.h"

// Redirect requests must never inherit a cookie header chosen for another host.
@interface CosImageRedirectDelegate : NSObject <NSURLSessionTaskDelegate> @end
@implementation CosImageRedirectDelegate
- (void)URLSession:(NSURLSession *)session task:(NSURLSessionTask *)task willPerformHTTPRedirection:(NSHTTPURLResponse *)response newRequest:(NSURLRequest *)request completionHandler:(void (^)(NSURLRequest * _Nullable))completionHandler {
    NSURL *url = request.URL;
    BOOL allowed = [url.scheme isEqual:@"https"] && ([url.host isEqual:@"img.nncos.com"] ||
        ([url.host isEqual:@"nncos.us"] && [url.path hasPrefix:@"/nnpic2/"]));
    if (!allowed) { completionHandler(nil); return; }
    NSMutableURLRequest *scoped = [request mutableCopy];
    scoped.HTTPShouldHandleCookies = NO;
    NSString *cookies = [HRBridgeModule currentNncosDownloadCookiesForURL:url];
    [scoped setValue:cookies.length ? cookies : nil forHTTPHeaderField:@"Cookie"];
    completionHandler(scoped);
}
@end

@implementation CosImageFileDownloader
- (NSString *)download:(NSDictionary *)task index:(NSUInteger)index checkpoint:(CosTargetCheckpoint)checkpoint error:(NSError **)error {
    NSURL *imageURL = [NSURL URLWithString:task[@"imageUrls"][index]];
    if (!imageURL) { if (error) *error = [NSError errorWithDomain:@"CosDownload" code:1 userInfo:@{NSLocalizedDescriptionKey:@"图片地址无效"}]; return nil; }
    NSURL *documents = [NSFileManager.defaultManager URLsForDirectory:NSDocumentDirectory inDomains:NSUserDomainMask].firstObject;
    NSURL *directory = [documents URLByAppendingPathComponent:[@"CosDownloads/" stringByAppendingString:task[@"taskId"]]];
    if (![NSFileManager.defaultManager createDirectoryAtURL:directory withIntermediateDirectories:YES attributes:nil error:error]) return nil;
    NSString *extension = imageURL.pathExtension.lowercaseString;
    if (![@[@"jpg", @"jpeg", @"png", @"webp", @"gif", @"avif"] containsObject:extension]) extension = @"img";
    NSURL *target = [directory URLByAppendingPathComponent:[NSString stringWithFormat:@"%lu.%@", (unsigned long)index, extension]];
    if (!checkpoint(target.path, error)) return nil;
    NSDictionary *existing = [NSFileManager.defaultManager attributesOfItemAtPath:target.path error:nil];
    if ([existing[NSFileSize] unsignedLongLongValue] > 0) return target.path;
    NSURL *partial = [NSURL fileURLWithPath:[target.path stringByAppendingString:@".part"]];
    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:imageURL];
    request.timeoutInterval = 30;
    [request setValue:task[@"referer"] forHTTPHeaderField:@"Referer"];
    [request setValue:@"Mozilla/5.0 (iPhone) AppleWebKit/605.1.15" forHTTPHeaderField:@"User-Agent"];
    [request setValue:@"identity" forHTTPHeaderField:@"Accept-Encoding"];
    request.HTTPShouldHandleCookies = NO;
    NSString *cookies = [HRBridgeModule currentNncosDownloadCookiesForURL:imageURL];
    if (cookies.length) [request setValue:cookies forHTTPHeaderField:@"Cookie"];
    dispatch_semaphore_t done = dispatch_semaphore_create(0);
    __block NSError *failure = nil;
    NSURLSession *session = [NSURLSession sessionWithConfiguration:NSURLSessionConfiguration.ephemeralSessionConfiguration delegate:[CosImageRedirectDelegate new] delegateQueue:nil];
    NSURLSessionDownloadTask *download = [session downloadTaskWithRequest:request completionHandler:^(NSURL *fileURL, NSURLResponse *response, NSError *networkError) {
        if (networkError) failure = networkError;
        else {
            NSHTTPURLResponse *http = (NSHTTPURLResponse *)response;
            if (http.statusCode < 200 || http.statusCode >= 300 || ![response.MIMEType.lowercaseString hasPrefix:@"image/"]) {
                failure = [NSError errorWithDomain:@"CosDownload" code:5 userInfo:@{NSLocalizedDescriptionKey:@"图片内容不可用，请检查登录状态"}];
            } else {
                // The URLSession temporary file is only valid during this callback.
                [NSFileManager.defaultManager removeItemAtURL:partial error:nil];
                [NSFileManager.defaultManager copyItemAtURL:fileURL toURL:partial error:&failure];
                if (!failure) {
                    unsigned long long size = [[NSFileManager.defaultManager attributesOfItemAtPath:partial.path error:nil][NSFileSize] unsignedLongLongValue];
                    if (size == 0 || (response.expectedContentLength >= 0 && size != response.expectedContentLength)) {
                        failure = [NSError errorWithDomain:@"CosDownload" code:6 userInfo:@{NSLocalizedDescriptionKey:@"图片传输未完成"}];
                    }
                }
            }
        }
        dispatch_semaphore_signal(done);
    }];
    [download resume];
    dispatch_semaphore_wait(done, DISPATCH_TIME_FOREVER); // Dedicated native worker, never the main/state queue.
    [session finishTasksAndInvalidate];
    if (!failure) {
        NSFileHandle *handle = [NSFileHandle fileHandleForWritingAtPath:partial.path];
        @try { [handle synchronizeFile]; [handle closeFile]; }
        @catch (NSException *exception) { failure = [NSError errorWithDomain:@"CosDownload" code:7 userInfo:@{NSLocalizedDescriptionKey:@"无法保存图片"}]; }
        if (!failure) [NSFileManager.defaultManager moveItemAtURL:partial toURL:target error:&failure];
    }
    if (failure) { if (error) *error = failure; return nil; }
    return target.path;
}
@end
