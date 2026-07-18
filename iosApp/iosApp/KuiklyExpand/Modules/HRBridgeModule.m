#import "HRBridgeModule.h"

#import "KuiklyRenderViewController.h"
#import <OpenKuiklyIOSRender/NSObject+KR.h>
#import <WebKit/WebKit.h>

static NSString * const kNetbianHomeURL = @"https://pic.netbian.com/";
static NSString * const kNetbianLoginURL = @"https://pic.netbian.com/e/memberconnect/?apptype=qq";
static NSString * const kNetbianLoginSucceededKey = @"netbian_login_succeeded";
static NSString * const kNetbianDownloadedURLsKey = @"netbian_downloaded_urls";
static const NSUInteger kNetbianDownloadRecordLimit = 500;
static KuiklyRenderCallback gNetbianLoginCallback = nil;
static NSString * const kNncosHomeURL = @"https://www.nncos.com/";
static NSString * const kNncosLoginURL = @"https://www.nncos.com/login/?redirect_to=https%3A%2F%2Fwww.nncos.com%2F";
static NSString * const kNncosLoginSucceededKey = @"nncos_login_succeeded";
static KuiklyRenderCallback gNncosLoginCallback = nil;

@interface NetbianLoginViewController : UIViewController <WKNavigationDelegate>
@end

@interface NncosLoginViewController : UIViewController <WKNavigationDelegate>
@end

@interface HRBridgeModule ()
+ (void)finishNetbianLoginWithSuccess:(BOOL)success message:(NSString *)message;
+ (void)markNetbianLoginSucceeded;
+ (BOOL)isNetbianLoggedIn;
+ (BOOL)hasNetbianCookie;
+ (BOOL)hasNetbianLoginCookie;
+ (BOOL)isNetbianLoginSuccessURL:(NSString *)urlString;
+ (NSString *)netbianCookieHeader;
+ (void)syncWKNetbianCookiesWithCompletion:(dispatch_block_t)completion;
+ (void)finishNncosLoginWithSuccess:(BOOL)success message:(NSString *)message;
+ (void)markNncosLoginSucceeded;
+ (BOOL)isNncosLoggedIn;
+ (BOOL)hasNncosCookie;
+ (BOOL)hasNncosLoginCookie;
+ (void)syncWKNncosCookiesWithCompletion:(dispatch_block_t)completion;
@end

@interface NetbianLoginViewController ()
@property (nonatomic, strong) WKWebView *webView;
@property (nonatomic, assign) BOOL callbackSent;
@end

@implementation NetbianLoginViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    self.title = @"Netbian Login";
    self.view.backgroundColor = UIColor.whiteColor;
    self.navigationItem.leftBarButtonItem = [[UIBarButtonItem alloc] initWithTitle:@"Close" style:UIBarButtonItemStylePlain target:self action:@selector(closeTapped)];
    self.navigationItem.rightBarButtonItem = [[UIBarButtonItem alloc] initWithTitle:@"Done" style:UIBarButtonItemStylePlain target:self action:@selector(doneTapped)];

    WKWebViewConfiguration *configuration = [[WKWebViewConfiguration alloc] init];
    configuration.websiteDataStore = WKWebsiteDataStore.defaultDataStore;
    self.webView = [[WKWebView alloc] initWithFrame:self.view.bounds configuration:configuration];
    self.webView.navigationDelegate = self;
    self.webView.customUserAgent = @"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    self.webView.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    [self.view addSubview:self.webView];
    [self.webView loadRequest:[NSURLRequest requestWithURL:[NSURL URLWithString:kNetbianLoginURL]]];
}

- (void)closeTapped {
    [self finishWithCurrentState:@"login page closed"];
}

- (void)doneTapped {
    [self finishWithCurrentState:@"login state checked"];
}

- (void)viewDidDisappear:(BOOL)animated {
    [super viewDidDisappear:animated];
    if (!self.callbackSent && (self.isBeingDismissed || self.navigationController.isBeingDismissed)) {
        [self finishWithCurrentState:@"login page dismissed"];
    }
}

- (void)webView:(WKWebView *)webView decidePolicyForNavigationAction:(WKNavigationAction *)navigationAction decisionHandler:(void (^)(WKNavigationActionPolicy))decisionHandler {
    NSURL *url = navigationAction.request.URL;
    NSString *scheme = url.scheme.lowercaseString ?: @"";
    if ([scheme isEqualToString:@"http"] || [scheme isEqualToString:@"https"]) {
        decisionHandler(WKNavigationActionPolicyAllow);
        return;
    }
    if ([scheme isEqualToString:@"wtloginmqq"]) {
        NSURLComponents *components = [NSURLComponents componentsWithURL:url resolvingAgainstBaseURL:NO];
        for (NSURLQueryItem *item in components.queryItems) {
            if ([item.name isEqualToString:@"p"] && [item.value hasPrefix:@"https://"]) {
                [webView loadRequest:[NSURLRequest requestWithURL:[NSURL URLWithString:item.value]]];
                break;
            }
        }
    }
    decisionHandler(WKNavigationActionPolicyCancel);
}

- (void)webView:(WKWebView *)webView didFinishNavigation:(WKNavigation *)navigation {
    NSString *urlString = webView.URL.absoluteString ?: @"";
    [self syncWKCookiesWithCompletion:^{
        if ([HRBridgeModule hasNetbianLoginCookie] || [HRBridgeModule isNetbianLoginSuccessURL:urlString]) {
            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(0.5 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
                [self syncWKCookiesWithCompletion:^{
                    [self finishWithLoginSuccess];
                }];
            });
        }
    }];
}

- (void)finishWithCurrentState:(NSString *)message {
    [self syncWKCookiesWithCompletion:^{
        if (!self.callbackSent) {
            [HRBridgeModule finishNetbianLoginWithSuccess:[HRBridgeModule isNetbianLoggedIn] message:message];
            self.callbackSent = YES;
        }
        [self dismissViewControllerAnimated:YES completion:nil];
    }];
}

- (void)finishWithLoginSuccess {
    [HRBridgeModule markNetbianLoginSucceeded];
    if (!self.callbackSent) {
        [HRBridgeModule finishNetbianLoginWithSuccess:YES message:@"login success"];
        self.callbackSent = YES;
    }
    [self dismissViewControllerAnimated:YES completion:nil];
}

- (void)syncWKCookiesWithCompletion:(dispatch_block_t)completion {
    [WKWebsiteDataStore.defaultDataStore.httpCookieStore getAllCookies:^(NSArray<NSHTTPCookie *> *cookies) {
        for (NSHTTPCookie *cookie in cookies) {
            if ([cookie.domain containsString:@"pic.netbian.com"]) {
                [[NSHTTPCookieStorage sharedHTTPCookieStorage] setCookie:cookie];
            }
        }
        dispatch_async(dispatch_get_main_queue(), ^{
            if (completion) {
                completion();
            }
        });
    }];
}

@end

@interface NncosLoginViewController ()
@property (nonatomic, strong) WKWebView *webView;
@property (nonatomic, assign) BOOL callbackSent;
@end

@implementation NncosLoginViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    self.title = @"NNCOS Login";
    self.view.backgroundColor = UIColor.whiteColor;
    self.navigationItem.leftBarButtonItem = [[UIBarButtonItem alloc] initWithTitle:@"Close" style:UIBarButtonItemStylePlain target:self action:@selector(closeTapped)];
    self.navigationItem.rightBarButtonItem = [[UIBarButtonItem alloc] initWithTitle:@"Done" style:UIBarButtonItemStylePlain target:self action:@selector(doneTapped)];

    WKWebViewConfiguration *configuration = [[WKWebViewConfiguration alloc] init];
    configuration.websiteDataStore = WKWebsiteDataStore.defaultDataStore;
    self.webView = [[WKWebView alloc] initWithFrame:self.view.bounds configuration:configuration];
    self.webView.navigationDelegate = self;
    self.webView.customUserAgent = @"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    self.webView.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    [self.view addSubview:self.webView];
    [self.webView loadRequest:[NSURLRequest requestWithURL:[NSURL URLWithString:kNncosLoginURL]]];
}

- (void)closeTapped {
    [self finishWithCurrentState:@"login page closed"];
}

- (void)doneTapped {
    [self finishWithCurrentState:@"login state checked"];
}

- (void)viewDidDisappear:(BOOL)animated {
    [super viewDidDisappear:animated];
    if (!self.callbackSent && (self.isBeingDismissed || self.navigationController.isBeingDismissed)) {
        [self finishWithCurrentState:@"login page dismissed"];
    }
}

- (void)webView:(WKWebView *)webView decidePolicyForNavigationAction:(WKNavigationAction *)navigationAction decisionHandler:(void (^)(WKNavigationActionPolicy))decisionHandler {
    NSString *scheme = navigationAction.request.URL.scheme.lowercaseString ?: @"";
    decisionHandler(([scheme isEqualToString:@"http"] || [scheme isEqualToString:@"https"]) ? WKNavigationActionPolicyAllow : WKNavigationActionPolicyCancel);
}

- (void)webView:(WKWebView *)webView didFinishNavigation:(WKNavigation *)navigation {
    [self syncWKCookiesWithCompletion:nil];
}

- (void)finishWithCurrentState:(NSString *)message {
    [self syncWKCookiesWithCompletion:^{
        if (!self.callbackSent) {
            [HRBridgeModule finishNncosLoginWithSuccess:[HRBridgeModule isNncosLoggedIn] message:message];
            self.callbackSent = YES;
        }
        [self dismissViewControllerAnimated:YES completion:nil];
    }];
}

- (void)syncWKCookiesWithCompletion:(dispatch_block_t)completion {
    [HRBridgeModule syncWKNncosCookiesWithCompletion:completion];
}

@end

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
    if ([method isEqualToString:@"openNetbianLogin"]) {
        [self openNetbianLoginWithCallback:callback];
        return nil;
    }
    if ([method isEqualToString:@"getNetbianLoginState"]) {
        [self getNetbianLoginStateWithCallback:callback];
        return nil;
    }
    if ([method isEqualToString:@"openNncosLogin"]) {
        [self openNncosLoginWithCallback:callback];
        return nil;
    }
    if ([method isEqualToString:@"getNncosLoginState"]) {
        [self getNncosLoginStateWithCallback:callback];
        return nil;
    }
    if ([method isEqualToString:@"downloadNetbianImage"]) {
        [self downloadNetbianImageWithParams:params callback:callback];
        return nil;
    }
    if ([method isEqualToString:@"getNetbianDownloadRecords"]) {
        [self getNetbianDownloadRecordsWithCallback:callback];
        return nil;
    }
    if ([method isEqualToString:@"markNetbianImageDownloaded"]) {
        [self markNetbianImageDownloadedWithParams:params callback:callback];
        return nil;
    }
    return nil;
}

- (void)getNetbianDownloadRecordsWithCallback:(KuiklyRenderCallback _Nullable)callback {
    NSString *urls = [[NSUserDefaults standardUserDefaults] stringForKey:kNetbianDownloadedURLsKey] ?: @"";
    if (callback) {
        callback(@{ @"code": @(0), @"urls": urls });
    }
}

- (void)markNetbianImageDownloadedWithParams:(id _Nullable)params callback:(KuiklyRenderCallback _Nullable)callback {
    NSDictionary *dict = [self dictionaryFromParams:params];
    NSString *url = [dict[@"url"] isKindOfClass:NSString.class] ? [dict[@"url"] stringByTrimmingCharactersInSet:NSCharacterSet.whitespaceAndNewlineCharacterSet] : @"";
    if (url.length == 0) {
        if (callback) {
            callback(@{ @"code": @(-1), @"message": @"missing url" });
        }
        return;
    }
    NSString *stored = [[NSUserDefaults standardUserDefaults] stringForKey:kNetbianDownloadedURLsKey] ?: @"";
    NSMutableOrderedSet<NSString *> *records = [NSMutableOrderedSet orderedSet];
    [records addObject:url];
    for (NSString *value in [stored componentsSeparatedByString:@"\n"]) {
        NSString *trimmed = [value stringByTrimmingCharactersInSet:NSCharacterSet.whitespaceAndNewlineCharacterSet];
        if (trimmed.length > 0) {
            [records addObject:trimmed];
        }
    }
    while (records.count > kNetbianDownloadRecordLimit) {
        [records removeObjectAtIndex:records.count - 1];
    }
    [[NSUserDefaults standardUserDefaults] setObject:[[records array] componentsJoinedByString:@"\n"] forKey:kNetbianDownloadedURLsKey];
    if (callback) {
        callback(@{ @"code": @(0) });
    }
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
    NSString *cookie = [HRBridgeModule netbianCookieHeader];
    if (cookie.length > 0) {
        [request setValue:cookie forHTTPHeaderField:@"Cookie"];
    }

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

- (void)openNetbianLoginWithCallback:(KuiklyRenderCallback _Nullable)callback {
    if (gNetbianLoginCallback) {
        gNetbianLoginCallback(@{@"code": @(-1), @"isLoggedIn": @([HRBridgeModule isNetbianLoggedIn]), @"message": @"login replaced"});
    }
    gNetbianLoginCallback = [callback copy];
    UIViewController *presentingViewController = [self topViewController];
    NetbianLoginViewController *loginViewController = [[NetbianLoginViewController alloc] init];
    UINavigationController *navigationController = [[UINavigationController alloc] initWithRootViewController:loginViewController];
    navigationController.modalPresentationStyle = UIModalPresentationFullScreen;
    [presentingViewController presentViewController:navigationController animated:YES completion:nil];
}

- (void)getNetbianLoginStateWithCallback:(KuiklyRenderCallback _Nullable)callback {
    [HRBridgeModule syncWKNetbianCookiesWithCompletion:^{
        if (callback) {
            callback(@{@"code": @(0), @"isLoggedIn": @([HRBridgeModule isNetbianLoggedIn]), @"hasCookie": @([HRBridgeModule hasNetbianCookie])});
        }
    }];
}

- (void)openNncosLoginWithCallback:(KuiklyRenderCallback _Nullable)callback {
    if (gNncosLoginCallback) {
        gNncosLoginCallback(@{ @"code": @(-1), @"isLoggedIn": @([HRBridgeModule isNncosLoggedIn]), @"message": @"login replaced" });
    }
    gNncosLoginCallback = [callback copy];
    UIViewController *presentingViewController = [self topViewController];
    NncosLoginViewController *loginViewController = [[NncosLoginViewController alloc] init];
    UINavigationController *navigationController = [[UINavigationController alloc] initWithRootViewController:loginViewController];
    navigationController.modalPresentationStyle = UIModalPresentationFullScreen;
    [presentingViewController presentViewController:navigationController animated:YES completion:nil];
}

- (void)getNncosLoginStateWithCallback:(KuiklyRenderCallback _Nullable)callback {
    [HRBridgeModule syncWKNncosCookiesWithCompletion:^{
        if (callback) {
            callback(@{ @"code": @(0), @"isLoggedIn": @([HRBridgeModule isNncosLoggedIn]), @"hasCookie": @([HRBridgeModule hasNncosCookie]) });
        }
    }];
}

- (void)downloadNetbianImageWithParams:(id _Nullable)params callback:(KuiklyRenderCallback _Nullable)callback {
    NSDictionary *dict = [self dictionaryFromParams:params];
    NSString *urlString = dict[@"url"];
    NSString *title = dict[@"title"] ?: @"netbian";
    NSString *referer = [dict[@"referer"] isKindOfClass:NSString.class] && [dict[@"referer"] length] > 0 ? dict[@"referer"] : kNetbianHomeURL;
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
    [self downloadNetbianImageURL:url title:title referer:referer callback:callback redirectDepth:0];
}

- (void)downloadNetbianImageURL:(NSURL *)url title:(NSString *)title referer:(NSString *)referer callback:(KuiklyRenderCallback _Nullable)callback redirectDepth:(NSInteger)redirectDepth {
    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:url];
    request.timeoutInterval = 30;
    [request setValue:@"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36" forHTTPHeaderField:@"User-Agent"];
    [request setValue:@"image/avif,image/webp,image/apng,image/*,*/*;q=0.8" forHTTPHeaderField:@"Accept"];
    [request setValue:referer ?: kNetbianHomeURL forHTTPHeaderField:@"Referer"];
    NSString *cookie = [HRBridgeModule netbianCookieHeader];
    if (cookie.length > 0) {
        [request setValue:cookie forHTTPHeaderField:@"Cookie"];
    }
    NSURLSessionDataTask *task = [NSURLSession.sharedSession dataTaskWithRequest:request completionHandler:^(NSData *data, NSURLResponse *response, NSError *error) {
        if (error) {
            dispatch_async(dispatch_get_main_queue(), ^{
                if (callback) {
                    callback(@{@"code": @(-1), @"message": error.localizedDescription ?: @"download failed"});
                }
            });
            return;
        }
        NSHTTPURLResponse *httpResponse = (NSHTTPURLResponse *)response;
        if ([httpResponse isKindOfClass:NSHTTPURLResponse.class] && (httpResponse.statusCode < 200 || httpResponse.statusCode > 299)) {
            dispatch_async(dispatch_get_main_queue(), ^{
                if (callback) {
                    callback(@{@"code": @(-1), @"message": [NSString stringWithFormat:@"http %ld", (long)httpResponse.statusCode]});
                }
            });
            return;
        }
        NSString *contentType = httpResponse.MIMEType ?: @"";
        if ([self netbianDataLooksLikeImage:data contentType:contentType]) {
            [self saveNetbianImageData:data title:title contentType:contentType callback:callback];
            return;
        }
        NSString *body = [[NSString alloc] initWithData:data ?: NSData.data encoding:NSUTF8StringEncoding] ?: @"";
        NSDictionary *json = nil;
        if ([body stringByTrimmingCharactersInSet:NSCharacterSet.whitespaceAndNewlineCharacterSet].length > 0) {
            json = [NSJSONSerialization JSONObjectWithData:data options:0 error:nil];
        }
        if ([json isKindOfClass:NSDictionary.class]) {
            NSString *pic = [json[@"pic"] isKindOfClass:NSString.class] ? json[@"pic"] : @"";
            if (pic.length > 0 && redirectDepth < 3) {
                NSURL *nextURL = [self absoluteNetbianURL:pic baseURL:url];
                if (nextURL) {
                    [self downloadNetbianImageURL:nextURL title:title referer:referer callback:callback redirectDepth:redirectDepth + 1];
                    return;
                }
            }
            NSNumber *msg = [json[@"msg"] isKindOfClass:NSNumber.class] ? json[@"msg"] : nil;
            NSString *info = [json[@"info"] isKindOfClass:NSString.class] ? json[@"info"] : @"";
            NSString *message = info.length > 0 ? info : ([msg integerValue] == 0 ? @"please login before downloading original image" : @"original image url not found");
            dispatch_async(dispatch_get_main_queue(), ^{
                if (callback) {
                    callback(@{@"code": @(-1), @"message": message});
                }
            });
            return;
        }
        dispatch_async(dispatch_get_main_queue(), ^{
            if (callback) {
                callback(@{@"code": @(-1), @"message": @"image content not found"});
            }
        });
    }];
    [task resume];
}

- (BOOL)netbianDataLooksLikeImage:(NSData *)data contentType:(NSString *)contentType {
    if ([contentType.lowercaseString hasPrefix:@"image/"]) {
        return YES;
    }
    if (data.length < 4) {
        return NO;
    }
    const unsigned char *bytes = data.bytes;
    return (bytes[0] == 0xFF && bytes[1] == 0xD8) ||
        (bytes[0] == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) ||
        (bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46);
}

- (NSURL *)absoluteNetbianURL:(NSString *)urlString baseURL:(NSURL *)baseURL {
    if ([urlString hasPrefix:@"//"]) {
        return [NSURL URLWithString:[@"https:" stringByAppendingString:urlString]];
    }
    if ([urlString hasPrefix:@"http://"] || [urlString hasPrefix:@"https://"]) {
        return [NSURL URLWithString:urlString];
    }
    if ([urlString hasPrefix:@"/"]) {
        return [NSURL URLWithString:[@"https://pic.netbian.com" stringByAppendingString:urlString]];
    }
    return [NSURL URLWithString:urlString relativeToURL:baseURL].absoluteURL;
}

- (void)saveNetbianImageData:(NSData *)data title:(NSString *)title contentType:(NSString *)contentType callback:(KuiklyRenderCallback _Nullable)callback {
    NSString *extension = [contentType.lowercaseString containsString:@"png"] ? @"png" : ([contentType.lowercaseString containsString:@"webp"] ? @"webp" : @"jpg");
    NSString *fileName = [NSString stringWithFormat:@"%@_%lld.%@", [self safeFileName:title], (long long)(NSDate.date.timeIntervalSince1970 * 1000), extension];
    NSURL *documentsURL = [NSFileManager.defaultManager URLsForDirectory:NSDocumentDirectory inDomains:NSUserDomainMask].firstObject;
    NSURL *targetURL = [documentsURL URLByAppendingPathComponent:fileName];
    NSError *writeError = nil;
    [data writeToURL:targetURL options:NSDataWritingAtomic error:&writeError];
    dispatch_async(dispatch_get_main_queue(), ^{
        if (callback) {
            if (writeError) {
                callback(@{@"code": @(-1), @"message": writeError.localizedDescription ?: @"save failed"});
            } else {
                callback(@{@"code": @(0), @"message": @"download finished", @"path": targetURL.path ?: @""});
            }
        }
    });
}

+ (void)finishNetbianLoginWithSuccess:(BOOL)success message:(NSString *)message {
    if (success) {
        [self markNetbianLoginSucceeded];
    }
    if (gNetbianLoginCallback) {
        gNetbianLoginCallback(@{@"code": @(0), @"isLoggedIn": @(success || [self isNetbianLoggedIn]), @"message": message ?: @""});
        gNetbianLoginCallback = nil;
    }
}

+ (void)markNetbianLoginSucceeded {
    [[NSUserDefaults standardUserDefaults] setBool:YES forKey:kNetbianLoginSucceededKey];
    [[NSUserDefaults standardUserDefaults] synchronize];
}

+ (BOOL)isNetbianLoggedIn {
    if ([self hasNetbianLoginCookie]) {
        [self markNetbianLoginSucceeded];
        return YES;
    }
    if (![self hasNetbianCookie]) {
        [[NSUserDefaults standardUserDefaults] setBool:NO forKey:kNetbianLoginSucceededKey];
        [[NSUserDefaults standardUserDefaults] synchronize];
        return NO;
    }
    return [[NSUserDefaults standardUserDefaults] boolForKey:kNetbianLoginSucceededKey];
}

+ (BOOL)hasNetbianCookie {
    NSURL *url = [NSURL URLWithString:kNetbianHomeURL];
    return [[NSHTTPCookieStorage sharedHTTPCookieStorage] cookiesForURL:url].count > 0;
}

+ (BOOL)hasNetbianLoginCookie {
    NSURL *url = [NSURL URLWithString:kNetbianHomeURL];
    NSArray<NSHTTPCookie *> *cookies = [[NSHTTPCookieStorage sharedHTTPCookieStorage] cookiesForURL:url];
    NSSet<NSString *> *loginKeys = [NSSet setWithArray:@[
        @"ecmsmluserid", @"ecmsmlusername", @"ecmsmlgroupid", @"ecmsmlrnd",
        @"mluserid", @"mlusername", @"mlgroupid", @"mlrnd",
        @"enewsuserid", @"enewsusername", @"userid", @"username"
    ]];
    for (NSHTTPCookie *cookie in cookies) {
        if ([loginKeys containsObject:cookie.name.lowercaseString] && cookie.value.length > 0) {
            return YES;
        }
    }
    return NO;
}

+ (BOOL)isNetbianLoginSuccessURL:(NSString *)urlString {
    return [urlString rangeOfString:@"/e/memberconnect/qq/loginend.php" options:NSCaseInsensitiveSearch].location != NSNotFound ||
        [urlString rangeOfString:@"/e/member/cp/" options:NSCaseInsensitiveSearch].location != NSNotFound;
}

+ (NSString *)netbianCookieHeader {
    NSURL *url = [NSURL URLWithString:kNetbianHomeURL];
    NSArray<NSHTTPCookie *> *cookies = [[NSHTTPCookieStorage sharedHTTPCookieStorage] cookiesForURL:url];
    NSMutableArray<NSString *> *pairs = [NSMutableArray array];
    for (NSHTTPCookie *cookie in cookies) {
        if (cookie.name.length > 0 && cookie.value.length > 0) {
            [pairs addObject:[NSString stringWithFormat:@"%@=%@", cookie.name, cookie.value]];
        }
    }
    return [pairs componentsJoinedByString:@"; "];
}

+ (void)syncWKNetbianCookiesWithCompletion:(dispatch_block_t)completion {
    [WKWebsiteDataStore.defaultDataStore.httpCookieStore getAllCookies:^(NSArray<NSHTTPCookie *> *cookies) {
        for (NSHTTPCookie *cookie in cookies) {
            if ([cookie.domain containsString:@"pic.netbian.com"]) {
                [[NSHTTPCookieStorage sharedHTTPCookieStorage] setCookie:cookie];
            }
        }
        dispatch_async(dispatch_get_main_queue(), ^{
            if (completion) {
                completion();
            }
        });
    }];
}

+ (void)finishNncosLoginWithSuccess:(BOOL)success message:(NSString *)message {
    if (success) {
        [self markNncosLoginSucceeded];
    }
    if (gNncosLoginCallback) {
        gNncosLoginCallback(@{ @"code": @(0), @"isLoggedIn": @(success || [self isNncosLoggedIn]), @"message": message ?: @"" });
        gNncosLoginCallback = nil;
    }
}

+ (void)markNncosLoginSucceeded {
    [[NSUserDefaults standardUserDefaults] setBool:YES forKey:kNncosLoginSucceededKey];
    [[NSUserDefaults standardUserDefaults] synchronize];
}

+ (BOOL)isNncosLoggedIn {
    if ([self hasNncosLoginCookie]) {
        [self markNncosLoginSucceeded];
        return YES;
    }
    if (![self hasNncosCookie]) {
        [[NSUserDefaults standardUserDefaults] setBool:NO forKey:kNncosLoginSucceededKey];
        [[NSUserDefaults standardUserDefaults] synchronize];
        return NO;
    }
    return [[NSUserDefaults standardUserDefaults] boolForKey:kNncosLoginSucceededKey];
}

+ (BOOL)hasNncosCookie {
    NSURL *url = [NSURL URLWithString:kNncosHomeURL];
    return [[NSHTTPCookieStorage sharedHTTPCookieStorage] cookiesForURL:url].count > 0;
}

+ (BOOL)hasNncosLoginCookie {
    NSURL *url = [NSURL URLWithString:kNncosHomeURL];
    for (NSHTTPCookie *cookie in [[NSHTTPCookieStorage sharedHTTPCookieStorage] cookiesForURL:url]) {
        if ([cookie.name.lowercaseString hasPrefix:@"wordpress_logged_in_"] && cookie.value.length > 0) {
            return YES;
        }
    }
    return NO;
}

+ (void)syncWKNncosCookiesWithCompletion:(dispatch_block_t)completion {
    [WKWebsiteDataStore.defaultDataStore.httpCookieStore getAllCookies:^(NSArray<NSHTTPCookie *> *cookies) {
        for (NSHTTPCookie *cookie in cookies) {
            if ([cookie.domain containsString:@"nncos.com"]) {
                [[NSHTTPCookieStorage sharedHTTPCookieStorage] setCookie:cookie];
            }
        }
        dispatch_async(dispatch_get_main_queue(), ^{
            if (completion) {
                completion();
            }
        });
    }];
}

- (UIViewController *)topViewController {
    UIViewController *rootViewController = nil;
    if (@available(iOS 13.0, *)) {
        for (UIScene *scene in UIApplication.sharedApplication.connectedScenes) {
            if (scene.activationState == UISceneActivationStateForegroundActive && [scene isKindOfClass:UIWindowScene.class]) {
                UIWindowScene *windowScene = (UIWindowScene *)scene;
                for (UIWindow *window in windowScene.windows) {
                    if (window.isKeyWindow) {
                        rootViewController = window.rootViewController;
                        break;
                    }
                }
            }
            if (rootViewController) {
                break;
            }
        }
    }
    if (!rootViewController) {
        rootViewController = UIApplication.sharedApplication.keyWindow.rootViewController;
    }
    UIViewController *topViewController = rootViewController;
    while (topViewController.presentedViewController) {
        topViewController = topViewController.presentedViewController;
    }
    if ([topViewController isKindOfClass:UINavigationController.class]) {
        topViewController = ((UINavigationController *)topViewController).topViewController;
    }
    return topViewController ?: rootViewController;
}

- (NSString *)safeFileName:(NSString *)title {
    NSString *base = title.length > 0 ? title : [NSString stringWithFormat:@"netbian_%lld", (long long)([[NSDate date] timeIntervalSince1970] * 1000)];
    NSCharacterSet *invalidSet = [NSCharacterSet characterSetWithCharactersInString:@"\\/:*?\"<>| \n\r\t"];
    NSArray<NSString *> *parts = [base componentsSeparatedByCharactersInSet:invalidSet];
    NSString *fileName = [parts componentsJoinedByString:@"_"];
    while ([fileName containsString:@"__"]) {
        fileName = [fileName stringByReplacingOccurrencesOfString:@"__" withString:@"_"];
    }
    if (fileName.length > 80) {
        fileName = [fileName substringToIndex:80];
    }
    return fileName.length > 0 ? fileName : @"netbian";
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
