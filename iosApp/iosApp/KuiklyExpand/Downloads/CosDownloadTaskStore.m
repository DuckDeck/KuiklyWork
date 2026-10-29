#import "CosDownloadTaskStore.h"
@interface CosDownloadTaskStore ()
@property NSURL *url;
@end
@implementation CosDownloadTaskStore
- (instancetype)initWithURL:(NSURL *)url { if ((self = [super init])) _url = url; return self; }
- (NSURL *)backup { return [NSURL fileURLWithPath:[self.url.path stringByAppendingString:@".bak"]]; }
- (NSArray *)decode:(NSData *)data {
    if (!data) return nil;
    id value = [NSJSONSerialization JSONObjectWithData:data options:0 error:nil];
    if (![value isKindOfClass:NSDictionary.class] || ![value[@"version"] isEqual:@1] || ![value[@"tasks"] isKindOfClass:NSArray.class]) return nil;
    for (id task in value[@"tasks"]) {
        if (![task isKindOfClass:NSDictionary.class] || ![task[@"detailUrl"] isKindOfClass:NSString.class] ||
            ![task[@"taskId"] isKindOfClass:NSString.class] || ![task[@"imageUrls"] isKindOfClass:NSArray.class] ||
            ![task[@"title"] isKindOfClass:NSString.class] || ![task[@"referer"] isKindOfClass:NSString.class] ||
            ![task[@"targets"] isKindOfClass:NSDictionary.class] || ![task[@"completed"] isKindOfClass:NSNumber.class] ||
            [task[@"completed"] integerValue] < 0 || [task[@"completed"] unsignedIntegerValue] > [task[@"imageUrls"] count] ||
            ![@[@"running", @"interrupted", @"completed"] containsObject:task[@"status"]]) return nil;
        for (id url in task[@"imageUrls"]) if (![url isKindOfClass:NSString.class]) return nil;
    }
    return value[@"tasks"];
}
- (NSArray *)load:(NSError **)error {
    BOOL exists = [NSFileManager.defaultManager fileExistsAtPath:self.url.path] || [NSFileManager.defaultManager fileExistsAtPath:self.backup.path];
    if (!exists) return @[];
    NSArray *tasks = [self decode:[NSData dataWithContentsOfURL:self.url]] ?: [self decode:[NSData dataWithContentsOfURL:self.backup]];
    if (!tasks && error) *error = [NSError errorWithDomain:@"CosDownload" code:1 userInfo:@{NSLocalizedDescriptionKey:@"下载进度文件损坏，已保留原文件"}];
    return tasks;
}
- (BOOL)write:(NSData *)data url:(NSURL *)url error:(NSError **)error {
    if (![data writeToURL:url options:NSDataWritingAtomic error:error]) return NO;
    NSFileHandle *handle = [NSFileHandle fileHandleForWritingAtPath:url.path];
    @try { [handle synchronizeFile]; [handle closeFile]; }
    @catch (NSException *exception) {
        if (error) *error = [NSError errorWithDomain:@"CosDownload" code:2 userInfo:@{NSLocalizedDescriptionKey:@"无法保存下载进度"}];
        return NO;
    }
    return handle != nil;
}
- (BOOL)save:(NSArray *)tasks error:(NSError **)error {
    if (![NSFileManager.defaultManager createDirectoryAtURL:self.url.URLByDeletingLastPathComponent withIntermediateDirectories:YES attributes:nil error:error]) return NO;
    NSData *data = [NSJSONSerialization dataWithJSONObject:@{@"version":@1, @"tasks":tasks} options:0 error:error];
    if (!data) return NO;
    NSData *previous = [NSData dataWithContentsOfURL:self.url];
    if ([self decode:previous] && ![self write:previous url:self.backup error:error]) return NO;
    return [self write:data url:self.url error:error];
}
@end
