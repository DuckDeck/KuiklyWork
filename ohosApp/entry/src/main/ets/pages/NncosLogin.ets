import router from '@ohos.router';
import web_webview from '@ohos.web.webview';

const LOGIN_URL = 'https://www.nncos.com/login/?redirect_to=https%3A%2F%2Fwww.nncos.com%2F';

@Entry
@Component
struct NncosLogin {
  private controller: web_webview.WebviewController = new web_webview.WebviewController();

  build() {
    Column() {
      Row() {
        Button('Close')
          .type(ButtonType.Normal)
          .onClick(() => router.back())
        Text('NNCOS Login')
          .fontSize(16)
          .fontColor('#333333')
        Button('Done')
          .type(ButtonType.Normal)
          .onClick(() => router.back())
      }
      .width('100%')
      .height(48)
      .padding({ left: 12, right: 12 })
      .justifyContent(FlexAlign.SpaceBetween)

      Web({ src: LOGIN_URL, controller: this.controller })
        .javaScriptAccess(true)
        .domStorageAccess(true)
        .width('100%')
        .layoutWeight(1)
    }
    .width('100%')
    .height('100%')
  }
}
