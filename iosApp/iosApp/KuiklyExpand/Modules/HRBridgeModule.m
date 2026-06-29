#import "HRBridgeModule.h"

#import "KuiklyRenderViewController.h"
#import <OpenKuiklyIOSRender/NSObject+KR.h>

#define REQ_PARAM_KEY @"reqParam"
#define CMD_KEY @"cmd"
#define FROM_HIPPY_RENDER @"from_hippy_render"
// 扩展桥接接口
/*
 * @brief Native暴露接口到kotlin侧，提供kotlin侧调用native能力
 */

@implementation HRBridgeModule

- (id _Nullable)hrv_callWithMethod:(NSString *)method params:(id _Nullable)params callback:(KuiklyRenderCallback _Nullable)callback {
    if ([method isEqualToString:@"fetchHtml"]) {
        [self fetchHtmlWithParams:params callback:callback];
        return nil;
    }
    if ([method isEqualToString:@"log"]) {
        NSDictionary *dict = [self dictionaryFromParams:params];
        NSString *content = dict[@"content"] ?: @"";
        NSLog(@"KuiklyRender:%@", content);
        return nil;
    }
    if ([method isEqualToString:@"copyToPasteboard"]) {
        NSDictionary *dict = [self dictionaryFromParams:params];
        NSString *content = dict[@"content"] ?: @"";
        UIPasteboard *pasteboard = [UIPasteboard generalPasteboard];
        pasteboard.string = content;
        return nil;
    }
    return nil;
}

- (void)fetchHtmlWithParams:(id _Nullable)params callback:(KuiklyRenderCallback _Nullable)callback {
    NSDictionary *dict = [self dictionaryFromParams:params];
    NSString *urlString = dict[@"url"];
    if (urlString.length == 0) {
        if (callback) {
            callback(@{@"code": @(-1), @"message": @"missing url"});
        }
        return;
    }
    NSURL *url = [NSURL URLWithString:urlString];
    if (!url) {
        if (callback) {
            callback(@{@"code": @(-1), @"message": @"invalid url"});
        }
        return;
    }
    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:url];
    request.HTTPMethod = @"GET";
    request.timeoutInterval = 10;
    [request setValue:@"Mozilla/5.0 KuiklyWork" forHTTPHeaderField:@"User-Agent"];
    [request setValue:@"text/html,application/xhtml+xml" forHTTPHeaderField:@"Accept"];

    NSURLSessionDataTask *task = [[NSURLSession sharedSession] dataTaskWithRequest:request completionHandler:^(NSData *data, NSURLResponse *response, NSError *error) {
        if (error) {
            if (callback) {
                callback(@{@"code": @(-1), @"message": error.localizedDescription ?: @"request failed"});
            }
            return;
        }
        NSInteger statusCode = 200;
        if ([response isKindOfClass:[NSHTTPURLResponse class]]) {
            statusCode = ((NSHTTPURLResponse *)response).statusCode;
        }
        if (statusCode < 200 || statusCode >= 300) {
            if (callback) {
                callback(@{@"code": @(-1), @"message": [NSString stringWithFormat:@"http %ld", (long)statusCode]});
            }
            return;
        }
        NSString *body = [self stringFromHtmlData:data response:response] ?: @"";
        if (callback) {
            callback(@{@"code": @(0), @"body": body});
        }
    }];
    [task resume];
}

- (NSDictionary *)dictionaryFromParams:(id _Nullable)params {
    if ([params isKindOfClass:[NSDictionary class]]) {
        return (NSDictionary *)params;
    }
    if ([params isKindOfClass:[NSString class]]) {
        NSData *data = [(NSString *)params dataUsingEncoding:NSUTF8StringEncoding];
        if (!data) {
            return @{};
        }
        NSDictionary *dict = [NSJSONSerialization JSONObjectWithData:data options:0 error:nil];
        if ([dict isKindOfClass:[NSDictionary class]]) {
            return dict;
        }
    }
    return @{};
}

- (NSString *)stringFromHtmlData:(NSData *)data response:(NSURLResponse *)response {
    if (data.length == 0) {
        return @"";
    }
    if ([response.textEncodingName length] > 0) {
        CFStringEncoding cfEncoding = CFStringConvertIANACharSetNameToEncoding((CFStringRef)response.textEncodingName);
        if (cfEncoding != kCFStringEncodingInvalidId) {
            NSStringEncoding encoding = CFStringConvertEncodingToNSStringEncoding(cfEncoding);
            NSString *text = [[NSString alloc] initWithData:data encoding:encoding];
            if (text.length > 0) {
                return text;
            }
        }
    }
    NSString *utf8 = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
    if (utf8.length > 0) {
        return utf8;
    }
    NSStringEncoding gb18030 = CFStringConvertEncodingToNSStringEncoding(kCFStringEncodingGB_18030_2000);
    NSString *gbText = [[NSString alloc] initWithData:data encoding:gb18030];
    if (gbText.length > 0) {
        return gbText;
    }
    return [[NSString alloc] initWithData:data encoding:NSISOLatin1StringEncoding];
}

@synthesize hr_rootView;

- (void)copyToPasteboard:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    NSString *content = params[@"content"];
    UIPasteboard *pasteboard = [UIPasteboard generalPasteboard];
    pasteboard.string = content;
}

- (void)log:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    NSString *content = params[@"content"];
    NSLog(@"KuiklyRender:%@", content);
}

@end