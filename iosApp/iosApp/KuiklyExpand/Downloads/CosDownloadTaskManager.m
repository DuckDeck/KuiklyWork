#import "CosDownloadTaskManager.h"
#import "CosImageFileDownloader.h"
#import "HRBridgeModule.h"

@implementation CosDownloadTaskManager {
    CosDownloadTaskStore *_store;
    CosTaskDownloadBlock _downloader;
    CosCompletionRecorder _recorder;
    CosDownloadedLookup _previouslyDownloaded;
    dispatch_queue_t _stateQueue;
    dispatch_queue_t _workerQueue;
    NSMutableDictionary<NSString *, NSDictionary *> *_records;
    NSMutableArray<NSString *> *_order;
    NSString *_activeID;
    NSError *_loadError;
}
+ (instancetype)sharedManager {
    static CosDownloadTaskManager *manager;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        NSURL *directory = [NSFileManager.defaultManager URLsForDirectory:NSApplicationSupportDirectory inDomains:NSUserDomainMask].firstObject;
        CosDownloadTaskStore *store = [[CosDownloadTaskStore alloc] initWithURL:[directory URLByAppendingPathComponent:@"CosDownloads/tasks.json"]];
        CosImageFileDownloader *downloader = [CosImageFileDownloader new];
        manager = [[self alloc] initWithStore:store downloader:^NSString *(NSDictionary *task, NSUInteger index, CosTargetCheckpoint checkpoint, NSError **error) {
            return [downloader download:task index:index checkpoint:checkpoint error:error];
        } recorder:^BOOL(NSString *url, NSError **error) { return [HRBridgeModule saveCompletedCosGroup:url error:error]; }
            previouslyDownloaded:^BOOL(NSString *url) { return [HRBridgeModule hasCompletedCosGroup:url]; }];
    });
    return manager;
}
- (instancetype)initWithStore:(CosDownloadTaskStore *)store downloader:(CosTaskDownloadBlock)downloader recorder:(CosCompletionRecorder)recorder {
    return [self initWithStore:store downloader:downloader recorder:recorder previouslyDownloaded:^BOOL(NSString *url) { return NO; }];
}
- (instancetype)initWithStore:(CosDownloadTaskStore *)store downloader:(CosTaskDownloadBlock)downloader recorder:(CosCompletionRecorder)recorder previouslyDownloaded:(CosDownloadedLookup)lookup {
    if ((self = [super init])) {
        _store = store; _downloader = [downloader copy]; _recorder = [recorder copy];
        _previouslyDownloaded = [lookup copy];
        _stateQueue = dispatch_queue_create("com.ss.kuiklywork.cos.state", DISPATCH_QUEUE_SERIAL);
        _workerQueue = dispatch_queue_create("com.ss.kuiklywork.cos.download", DISPATCH_QUEUE_SERIAL);
        _records = [NSMutableDictionary dictionary]; _order = [NSMutableArray array];
        NSError *loadError = nil;
        NSArray *loaded = [store load:&loadError];
        _loadError = loadError;
        for (NSDictionary *record in loaded) {
            NSMutableDictionary *task = [record mutableCopy];
            if ([task[@"status"] isEqual:@"running"]) { task[@"status"] = @"interrupted"; task[@"message"] = @"下载已中断，点击继续下载"; }
            _records[task[@"detailUrl"]] = [task copy]; [_order addObject:task[@"detailUrl"]];
        }
    }
    return self;
}
- (NSDictionary *)idle {
    return @{@"taskId":@"", @"detailUrl":@"", @"title":@"", @"status":@"idle", @"completed":@0, @"total":@0, @"message":_loadError.localizedDescription ?: @""};
}
- (NSDictionary *)publicTask:(NSDictionary *)task {
    if (!task) return [self idle];
    return @{@"taskId":task[@"taskId"], @"detailUrl":task[@"detailUrl"], @"title":task[@"title"], @"status":task[@"status"],
             @"completed":task[@"completed"], @"total":@([task[@"imageUrls"] count]), @"message":task[@"message"] ?: @""};
}
- (NSDictionary *)current:(NSString *)url {
    if (url) return _records[url];
    for (NSString *key in _order) if ([_records[key][@"taskId"] isEqual:_activeID]) return _records[key];
    for (NSString *key in _order.reverseObjectEnumerator) if ([_records[key][@"status"] isEqual:@"interrupted"]) return _records[key];
    return _order.lastObject ? _records[_order.lastObject] : nil;
}
- (NSDictionary *)snapshotForDetailURL:(NSString *)url {
    __block NSDictionary *snapshot;
    dispatch_sync(_stateQueue, ^{ snapshot = [self publicTask:[self current:url]]; });
    return snapshot;
}
- (NSArray<NSString *> *)visibleDownloadedURLs:(NSArray<NSString *> *)urls {
    __block NSArray *visible = nil;
    dispatch_sync(_stateQueue, ^{
        if (self->_loadError) return;
        NSMutableArray *result = [NSMutableArray array];
        for (NSString *url in urls) {
            NSDictionary *task = self->_records[url];
            if (!task || [task[@"status"] isEqual:@"completed"] || [task[@"previouslyDownloaded"] boolValue]) [result addObject:url];
        }
        visible = [result copy];
    });
    return visible;
}
- (BOOL)persist:(NSDictionary *)task error:(NSError **)error {
    NSMutableArray *order = [_order mutableCopy]; [order removeObject:task[@"detailUrl"]]; [order addObject:task[@"detailUrl"]];
    NSMutableArray *values = [NSMutableArray array];
    for (NSString *key in order) [values addObject:[key isEqual:task[@"detailUrl"]] ? task : _records[key]];
    if (![_store save:values error:error]) return NO;
    _order = order; _records[task[@"detailUrl"]] = [task copy]; return YES;
}
- (void)startWithRequest:(NSDictionary *)request completion:(void (^)(NSDictionary *))completion {
    __block NSDictionary *reply;
    dispatch_sync(_stateQueue, ^{
        NSString *url = [request[@"detailUrl"] isKindOfClass:NSString.class] ? request[@"detailUrl"] : @"";
        NSDictionary *active = [self current:nil];
        if (self->_activeID) {
            BOOL same = [active[@"detailUrl"] isEqual:url];
            reply = @{@"code":same ? @0 : @1, @"task":[self publicTask:active], @"message":same ? @"" : @"已有一组图片正在下载"}; return;
        }
        if (self->_loadError) { reply = @{@"code":@(-1), @"message":self->_loadError.localizedDescription}; return; }
        NSMutableDictionary *task = [self->_records[url] mutableCopy];
        if (![task[@"status"] isEqual:@"interrupted"]) {
            NSArray *input = [request[@"imageUrls"] isKindOfClass:NSArray.class] ? request[@"imageUrls"] : @[];
            BOOL valid = [url hasPrefix:@"https://www.nncos.com/"] && input.count > 0;
            for (id image in input) if (![image isKindOfClass:NSString.class] ||
                (![(NSString *)image hasPrefix:@"https://img.nncos.com/"] && ![(NSString *)image hasPrefix:@"https://nncos.us/nnpic2/"])) valid = NO;
            if (!valid) { reply = @{@"code":@(-1), @"message":@"图片清单无效或为空"}; return; }
            task = [@{@"taskId":NSUUID.UUID.UUIDString, @"detailUrl":url, @"title":[request[@"title"] isKindOfClass:NSString.class] ? request[@"title"] : @"",
                @"imageUrls":[NSOrderedSet orderedSetWithArray:input].array, @"referer":[request[@"referer"] isKindOfClass:NSString.class] ? request[@"referer"] : @"https://www.nncos.com/",
                @"completed":@0, @"targets":@{}, @"previouslyDownloaded":@(self->_previouslyDownloaded(url))} mutableCopy];
        }
        task[@"status"] = @"running"; task[@"message"] = @"";
        NSError *error = nil;
        if (![self persist:task error:&error]) { reply = @{@"code":@(-1), @"message":error.localizedDescription ?: @"无法保存任务"}; return; }
        self->_activeID = task[@"taskId"];
        reply = @{@"code":@0, @"task":[self publicTask:task]};
        NSString *identifier = task[@"taskId"];
        dispatch_async(self->_workerQueue, ^{ [self runTask:url identifier:identifier]; });
    });
    dispatch_async(dispatch_get_main_queue(), ^{ completion(reply); });
}
- (void)runTask:(NSString *)url identifier:(NSString *)identifier {
    __block NSError *failure = nil;
    while (!failure) {
        __block NSDictionary *task;
        dispatch_sync(_stateQueue, ^{ task = self->_records[url]; });
        NSUInteger index = [task[@"completed"] unsignedIntegerValue];
        if (index >= [task[@"imageUrls"] count]) break;
        NSString *target = _downloader(task, index, ^BOOL(NSString *location, NSError **error) {
            __block BOOL saved;
            __block NSError *writeError = nil;
            dispatch_sync(self->_stateQueue, ^{
                NSMutableDictionary *next = [self->_records[url] mutableCopy];
                NSMutableDictionary *targets = [next[@"targets"] mutableCopy]; targets[@(index).stringValue] = location;
                next[@"targets"] = [targets copy]; saved = [self persist:next error:&writeError];
            });
            if (error) *error = writeError; return saved;
        }, &failure);
        if (!target) { if (!failure) failure = [NSError errorWithDomain:@"CosDownload" code:3 userInfo:@{NSLocalizedDescriptionKey:@"图片保存失败"}]; break; }
        dispatch_sync(_stateQueue, ^{
            NSMutableDictionary *next = [self->_records[url] mutableCopy];
            NSMutableDictionary *targets = [next[@"targets"] mutableCopy]; targets[@(index).stringValue] = target;
            next[@"targets"] = [targets copy]; next[@"completed"] = @(index + 1);
            if (![self persist:next error:&failure] && !failure) failure = [NSError errorWithDomain:@"CosDownload" code:2 userInfo:@{NSLocalizedDescriptionKey:@"进度保存失败"}];
        });
    }
    if (!failure && !_recorder(url, &failure) && !failure) failure = [NSError errorWithDomain:@"CosDownload" code:4 userInfo:@{NSLocalizedDescriptionKey:@"下载记录保存失败"}];
    dispatch_sync(_stateQueue, ^{
        NSMutableDictionary *next = [self->_records[url] mutableCopy];
        next[@"status"] = failure ? @"interrupted" : @"completed"; next[@"message"] = failure.localizedDescription ?: @"";
        NSError *writeError = nil;
        if (![self persist:next error:&writeError]) {
            next[@"status"] = @"interrupted"; next[@"message"] = writeError.localizedDescription ?: @"进度保存失败";
            self->_records[url] = [next copy];
        }
        if ([self->_activeID isEqual:identifier]) self->_activeID = nil;
    });
}
@end
