#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>
#import <OpenKuiklyIOSRender/KRBaseModule.h>
NS_ASSUME_NONNULL_BEGIN

@interface HRBridgeModule : KRBaseModule
+ (BOOL)saveCompletedCosGroup:(NSString *)url error:(NSError **)error;
+ (NSString *)currentNncosDownloadCookiesForURL:(NSURL *)url;
+ (BOOL)hasCompletedCosGroup:(NSString *)url;

@end

NS_ASSUME_NONNULL_END
