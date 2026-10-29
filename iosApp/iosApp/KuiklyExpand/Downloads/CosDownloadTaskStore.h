#import <Foundation/Foundation.h>
NS_ASSUME_NONNULL_BEGIN
@interface CosDownloadTaskStore : NSObject
- (instancetype)initWithURL:(NSURL *)url;
- (nullable NSArray<NSDictionary *> *)load:(NSError **)error;
- (BOOL)save:(NSArray<NSDictionary *> *)tasks error:(NSError **)error;
@end
NS_ASSUME_NONNULL_END
