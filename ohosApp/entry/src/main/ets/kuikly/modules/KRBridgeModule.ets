import { common } from '@kit.AbilityKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { KRAny } from '@kuikly-open/render';
import { KuiklyRenderBaseModule } from '@kuikly-open/render';
import { KuiklyRenderCallback } from '@kuikly-open/render';
import util from '@ohos.util';
import fs from '@ohos.file.fs';
import { fileUri } from '@kit.CoreFileKit';
import { http } from '@kit.NetworkKit';
import { preferences } from '@kit.ArkData';
import router from '@ohos.router';
import web_webview from '@ohos.web.webview';

interface FetchHtmlResult {
  code: number;
  message?: string;
  body?: string;
}

export class KRBridgeModule extends KuiklyRenderBaseModule {
  static readonly MODULE_NAME = "HRBridgeModule";
  static readonly SHOW_ALERT = "showAlert";
  static readonly CLOSE_PAGE = "closePage";
  static readonly OPEN_PAGE = "openPage";
  static readonly COPY_TO_PASTE_BOARD = "copyToPasteboard";
  static readonly TOAST = "toast";
  static readonly LOG = "log";
  static readonly REPORT_DT = "reportDT";
  static readonly REPORT_REAL_TIME = "reportRealtime";
  static readonly LOCAL_SERVE_TIME = "localServeTime";
  static readonly CURRENT_TIME_STAMP = "currentTimestamp";
  static readonly DATE_FORMATTER = "dateFormatter";
  static readonly READ_ASSET_FILE = "readAssetFile";
  static readonly FETCH_HTML = "fetchHtml";
  static readonly OPEN_NNCOS_LOGIN = "openNncosLogin";
  static readonly GET_NNCOS_LOGIN_STATE = "getNncosLoginState";
  static readonly GET_NETBIAN_DOWNLOAD_RECORDS = "getNetbianDownloadRecords";
  static readonly MARK_NETBIAN_IMAGE_DOWNLOADED = "markNetbianImageDownloaded";

  syncMode(): boolean {
    return false;
  }

  call(method: string, params: KRAny, callback: KuiklyRenderCallback): KRAny {
    switch (method) {
      case KRBridgeModule.SHOW_ALERT: {
        this.showAlert(params, callback);
        break;
      }
      case KRBridgeModule.CLOSE_PAGE: {
        this.closePage();
        break;
      }
      case KRBridgeModule.OPEN_PAGE: {
        this.openPage();
        break;
      }
      case KRBridgeModule.COPY_TO_PASTE_BOARD: {
        this.copyToPasteboard();
        break;
      }
      case KRBridgeModule.TOAST: {
        this.toast();
        break;
      }
      case KRBridgeModule.LOG: {
        this.log(params);
        break;
      }
      case KRBridgeModule.REPORT_DT: {
        this.reportDT(params);
        break;
      }
      case KRBridgeModule.REPORT_REAL_TIME: {
        this.reportRealtime(params);
        break;
      }
      case KRBridgeModule.LOCAL_SERVE_TIME: {
        this.localServeTime(params, callback);
        break;
      }
      case KRBridgeModule.CURRENT_TIME_STAMP: {
        return this.currentTimestamp();
      }
      case KRBridgeModule.DATE_FORMATTER: {
        return this.dateFormatter(params);
      }
      case KRBridgeModule.READ_ASSET_FILE: {
        this.readAssetFile(params, callback);
        break;
      }
      case KRBridgeModule.FETCH_HTML: {
        this.fetchHtml(params, callback);
        break;
      }
      case KRBridgeModule.OPEN_NNCOS_LOGIN: {
        try {
          router.pushUrl({ url: 'pages/NncosLogin' });
          callback({ "code": 0, "isLoggedIn": this.isNncosLoggedIn(), "message": "login page opened" });
        } catch (e) {
          console.error(`[KuiklyWork] open NNCOS login failed: ${e}`);
          callback({ "code": -1, "isLoggedIn": this.isNncosLoggedIn(), "message": "open login page failed" });
        }
        break;
      }
      case KRBridgeModule.GET_NNCOS_LOGIN_STATE: {
        const hasCookie = this.hasNncosCookie();
        callback({ "code": 0, "isLoggedIn": this.isNncosLoggedIn(), "hasCookie": hasCookie });
        break;
      }
      case KRBridgeModule.GET_NETBIAN_DOWNLOAD_RECORDS: {
        this.getNetbianDownloadRecords(callback);
        break;
      }
      case KRBridgeModule.MARK_NETBIAN_IMAGE_DOWNLOADED: {
        this.markNetbianImageDownloaded(params, callback);
        break;
      }
      default:
        break;
    }
    return null;
  }

  private showAlert(params: KRAny, callback: KuiklyRenderCallback) {
  }

  // TODO close page
  private closePage() {
  }

  // TODO open new page
  private openPage() {
  }

  // TODO copy to paste board
  private copyToPasteboard() {
  }

  // TODO open toast
  private toast() {
  }

  private log(params: KRAny) {
    if (params == null) {
      return
    }
    console.log(`KuiklyRender ${JSON.stringify(params)}`,)
  }

  private reportDT(params: KRAny) {
  }

  private reportRealtime(params: KRAny) {
  }

  private localServeTime(params: KRAny, callback: KuiklyRenderCallback) {
    callback({ "time": Date.now() / 1000.0 });
  }

  private currentTimestamp(): string {
    return Date.now().toString()
  }

  // TODO format date
  private dateFormatter(params: KRAny): string {
    console.log(`========== ${params}`)
    return "";
  }

  private fetchHtml(params: KRAny, callback: KuiklyRenderCallback) {
    const url = this.readUrl(params);
    if (!url) {
      this.callbackFetchHtml(callback, { "code": -1, "message": "missing url" });
      return;
    }
    console.info(`[KuiklyWork] fetchHtml start: ${url}`);
    const request = http.createHttp();
    request.request(url, {
      method: http.RequestMethod.GET,
      header: {
        'User-Agent': 'Mozilla/5.0 KuiklyWork',
        'Accept': 'text/html,application/xhtml+xml'
      },
      expectDataType: http.HttpDataType.STRING,
      connectTimeout: 10000,
      readTimeout: 10000
    }, (err: BusinessError, data: http.HttpResponse) => {
      request.destroy();
      if (err) {
        console.error(`[KuiklyWork] fetchHtml failed: ${err.message}`);
        this.callbackFetchHtml(callback, { "code": -1, "message": err.message });
        return;
      }
      const code = data.responseCode;
      if (code < 200 || code >= 300) {
        console.error(`[KuiklyWork] fetchHtml http error: ${code}`);
        this.callbackFetchHtml(callback, { "code": -1, "message": `http ${code}` });
        return;
      }
      const body = this.responseToString(data.result);
      console.info(`[KuiklyWork] fetchHtml success, length: ${body.length}`);
      this.callbackFetchHtml(callback, { "code": 0, "body": body });
    });
  }

  private hasNncosCookie(): boolean {
    try {
      return web_webview.WebCookieManager.fetchCookieSync('https://www.nncos.com/').length > 0;
    } catch (e) {
      console.error(`[KuiklyWork] read NNCOS cookie failed: ${e}`);
      return false;
    }
  }

  private isNncosLoggedIn(): boolean {
    try {
      const cookie = web_webview.WebCookieManager.fetchCookieSync('https://www.nncos.com/');
      return cookie.split(';').some(value => value.trim().toLowerCase().startsWith('wordpress_logged_in_'));
    } catch (e) {
      console.error(`[KuiklyWork] read NNCOS login state failed: ${e}`);
      return false;
    }
  }

  private getNetbianDownloadRecords(callback: KuiklyRenderCallback) {
    try {
      const store = preferences.getPreferencesSync(getContext(this), { name: 'netbian_download_records' });
      const urls = store.getSync('downloaded_urls', '') as string;
      callback({ "code": 0, "urls": urls });
    } catch (e) {
      console.error(`[KuiklyWork] getNetbianDownloadRecords failed: ${e}`);
      callback({ "code": -1, "message": `${e}` });
    }
  }

  private markNetbianImageDownloaded(params: KRAny, callback: KuiklyRenderCallback) {
    const url = this.readUrl(params).trim();
    if (!url) {
      callback({ "code": -1, "message": "missing url" });
      return;
    }
    try {
      const store = preferences.getPreferencesSync(getContext(this), { name: 'netbian_download_records' });
      const stored = store.getSync('downloaded_urls', '') as string;
      const urls = [url, ...stored.split('\n').map(value => value.trim()).filter(value => value.length > 0 && value !== url)]
        .slice(0, 500)
        .join('\n');
      store.putSync('downloaded_urls', urls);
      store.flush((err: BusinessError) => {
        if (err) {
          console.error(`[KuiklyWork] markNetbianImageDownloaded flush failed: ${err.message}`);
          callback({ "code": -1, "message": err.message });
          return;
        }
        callback({ "code": 0 });
      });
    } catch (e) {
      console.error(`[KuiklyWork] markNetbianImageDownloaded failed: ${e}`);
      callback({ "code": -1, "message": `${e}` });
    }
  }

  private readUrl(params: KRAny): string {
    try {
      if (typeof params === 'string') {
        const records = JSON.parse(params) as Record<string, string>;
        return records["url"] || "";
      }
      if (params != null && typeof params === 'object') {
        const records = params as Record<string, string>;
        return records["url"] || "";
      }
    } catch (e) {
      console.error(`[KuiklyWork] fetchHtml parse params failed: ${e}`);
    }
    return "";
  }

  private callbackFetchHtml(callback: KuiklyRenderCallback, result: FetchHtmlResult) {
    try {
      callback(result);
    } catch (e) {
      console.error(`[KuiklyWork] fetchHtml callback failed: ${e}`);
    }
  }

  private responseToString(result: string | Object | ArrayBuffer): string {
    if (typeof result === 'string') {
      return result;
    }
    if (result instanceof ArrayBuffer) {
      const bytes = new Uint8Array(result);
      try {
        return new util.TextDecoder('utf-8').decodeWithStream(bytes);
      } catch (e) {
        return new util.TextDecoder().decodeWithStream(bytes);
      }
    }
    return JSON.stringify(result);
  }

  private readAssetFile(params: KRAny, callback: KuiklyRenderCallback) {
    // let startTimeMills = Date.now();
    let records = JSON.parse(params as string) as Record<string, string>;
    let assetPath = records["assetPath"];

    let context = getContext(this) as common.UIAbilityContext;
    /*
    context.resourceManager.getRawFileContent(assetPath, (error: BusinessError, value: Uint8Array) => {
      let textDecoder: util.TextDecoder = util.TextDecoder.create();
      let res: string = textDecoder.decodeWithStream(value);
      callback({
        "result": JSON.parse(res)
      });
    });
    */
    let resDir = context.resourceDir
    let path = resDir + '/' + assetPath
    let assetURI = fileUri.getUriFromPath(path)
    const file = fs.openSync(assetURI, fs.OpenMode.READ_ONLY);
    const stat = fs.statSync(file.fd);
    const arrayBuffer = new ArrayBuffer(stat.size);
    fs.read(file.fd, arrayBuffer, (err: BusinessError, readLen: number) => {
      if (err) {
        console.error("read failed with error message: " + err.message + ", error code: " + err.code);
      } else {
        let buf = new Uint8Array(arrayBuffer, 0, readLen);
        const textDecoder = new util.TextDecoder('utf-8');
        const res = textDecoder.decodeWithStream(new Uint8Array(buf));
        callback({
          'result': JSON.parse(res)
        });
      }
      fs.closeSync(file);
    });
  }
}
