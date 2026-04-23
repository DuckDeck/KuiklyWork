import SwiftUI
import shared

struct ContentView: View {

    var body: some View {
//        KuiklyRenderViewPage(pageName: "router", data: [:]).ignoresSafeArea()
        
        NavigationView {
                   VStack(spacing: 20) {
                       Text("首页")
                           .font(.title)
                       
                       // 👇 直接用 NavigationLink 做按钮，点击跳转
                       NavigationLink {
                           // 跳转目标页面
                           KuiklyViewPage()
                       } label: {
                           Text("点击进入新页面")
                               .font(.title)
                               .padding()
                               .background(Color.blue)
                               .foregroundColor(.white)
                               .cornerRadius(10)
                       }
                   }
               }
               // 适配 iPad 样式
               .navigationViewStyle(.stack)
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
