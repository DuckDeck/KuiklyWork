#import "CosDownloadTaskManager.h"
NS_ASSUME_NONNULL_BEGIN
@interface CosImageFileDownloader : NSObject
- (nullable NSString *)download:(NSDictionary *)task index:(NSUInteger)index checkpoint:(CosTargetCheckpoint)checkpoint error:(NSError **)error;
@end
NS_ASSUME_NONNULL_END
