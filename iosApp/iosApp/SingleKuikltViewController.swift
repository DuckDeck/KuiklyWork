//
//  SingleKuikltViewController.swift
//  iosApp
//
//  Created by Stan Hu on 2026/4/22.
//  Copyright © 2026 orgName. All rights reserved.
//

import Foundation
import OpenKuiklyIOSRender
import SwiftUI
class SingleKuikltViewController:UIViewController, KuiklyViewBaseDelegate{
    override func viewDidLoad() {
        super.viewDidLoad()
        let baseKv = KuiklyBaseView(frame: CGRect(x: 0, y: 100, width: UIScreen.main.bounds.width, height: 400), pageName: "image_adapter", pageData: [:], delegate: self, frameworkName: "shared")
        view.addSubview(baseKv)
        baseKv.viewWillAppear()
        baseKv.viewDidAppear()
    }
}

struct KuiklyViewPage: UIViewControllerRepresentable {
    //  typealiaUIViewControllerType = UINavigationController
    func makeUIViewController(context: Context) -> UINavigationController {
        let hrVC = SingleKuikltViewController()
        return UINavigationController.init(rootViewController: hrVC)
    }

    func updateUIViewController(_ uiViewController: UINavigationController, context: Context) {

    }

    func dealloc() {

    }

}
