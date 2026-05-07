//
//  KeyboardViewController.swift
//  KuiklyKeyboard
//
//  Created by Stan Hu on 2026/4/22.
//  Copyright © 2026 orgName. All rights reserved.
//

import UIKit
import OpenKuiklyIOSRender

class KeyboardViewController: UIInputViewController, KuiklyViewBaseDelegate {

    @IBOutlet var nextKeyboardButton: UIButton!
    
    var baseKv : KuiklyBaseView!
    override func updateViewConstraints() {
        super.updateViewConstraints()
        
        // Add custom view sizing constraints here
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        let heightConstraint = view.heightAnchor.constraint(equalToConstant: 320)
            heightConstraint.priority = .defaultHigh   // 999，避免和系统冲突
            heightConstraint.isActive = true
        
        // Perform custom UI setup here
        self.nextKeyboardButton = UIButton(type: .system)
        
        self.nextKeyboardButton.setTitle(NSLocalizedString("Next Keyboard", comment: "Title for 'Next Keyboard' button"), for: [])
        self.nextKeyboardButton.sizeToFit()
        self.nextKeyboardButton.translatesAutoresizingMaskIntoConstraints = false
        
        self.nextKeyboardButton.addTarget(self, action: #selector(handleInputModeList(from:with:)), for: .allTouchEvents)
        
        self.view.addSubview(self.nextKeyboardButton)
        
        self.nextKeyboardButton.leftAnchor.constraint(equalTo: self.view.leftAnchor).isActive = true
        self.nextKeyboardButton.bottomAnchor.constraint(equalTo: self.view.bottomAnchor).isActive = true
        
        baseKv = KuiklyBaseView(frame: CGRect(x: 0, y: 0, width: UIScreen.main.bounds.width, height: 320), pageName: "BaseKbPage", pageData: [:], delegate: self, frameworkName: "shared")
        view.addSubview(baseKv)
        
        
        NSLayoutConstraint.activate([
            baseKv.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            baseKv.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            baseKv.topAnchor.constraint(equalTo: view.topAnchor),
            baseKv.bottomAnchor.constraint(equalTo: nextKeyboardButton.topAnchor),
        ])

        
        baseKv.viewWillAppear()
        baseKv.viewDidAppear()
        
    }
    
    override func viewWillLayoutSubviews() {
        self.nextKeyboardButton.isHidden = !self.needsInputModeSwitchKey
        super.viewWillLayoutSubviews()
    }
    
    override func textWillChange(_ textInput: UITextInput?) {
        // The app is about to change the document's contents. Perform any preparation here.
    }
    
    override func textDidChange(_ textInput: UITextInput?) {
        // The app has just changed the document's contents, the document context has been updated.
        
        var textColor: UIColor
        let proxy = self.textDocumentProxy
        if proxy.keyboardAppearance == UIKeyboardAppearance.dark {
            textColor = UIColor.white
        } else {
            textColor = UIColor.black
        }
        self.nextKeyboardButton.setTitleColor(textColor, for: [])
    }

}
