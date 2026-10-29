#import <Foundation/Foundation.h>
#import "CosDownloadTaskStore.h"
NS_ASSUME_NONNULL_BEGIN
typedef BOOL (^CosTargetCheckpoint)(NSString *target, NSError **error);
typedef NSString * _Nullable (^CosTaskDownloadBlock)(NSDictionary *task, NSUInteger index, CosTargetCheckpoint checkpoint, NSError **error);
typedef BOOL (^CosCompletionRecorder)(NSString *detailURL, NSError **error);
typedef BOOL (^CosDownloadedLookup)(NSString *detailURL);
@interface CosDownloadTaskManager : NSObject
+ (instancetype)sharedManager;
- (instancetype)initWithStore:(CosDownloadTaskStore *)store downloader:(CosTaskDownloadBlock)downloader recorder:(CosCompletionRecorder)recorder;
- (instancetype)initWithStore:(CosDownloadTaskStore *)store downloader:(CosTaskDownloadBlock)downloader recorder:(CosCompletionRecorder)recorder previouslyDownloaded:(CosDownloadedLookup)lookup;
- (void)startWithRequest:(NSDictionary *)request completion:(void (^)(NSDictionary *reply))completion;
- (NSDictionary *)snapshotForDetailURL:(nullable NSString *)detailURL;
- (nullable NSArray<NSString *> *)visibleDownloadedURLs:(NSArray<NSString *> *)urls;
@end
NS_ASSUME_NONNULL_END
