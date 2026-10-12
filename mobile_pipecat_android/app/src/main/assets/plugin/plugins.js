function callbackListener(result) {
    console.log("callbackListener result=" + JSON.stringify(result))
    if (pluginService.dispatchResult(result)) {
        return
    } else {
        alert("requestId: " + result.requestId + "\nresultCode: " + result.resultCode + "\n" + result.params)
    }
}

function callbackAppEvent(event) {
    console.log("callbackAppEvent event=" + JSON.stringify(event))
    let params = JSON.parse(event.params)

    if (event.eventName == "Navigate" && event.eventType == "back") {
        pluginService.goPrevPageWithData()
        return
    }
}

const pluginService = (function () {
    const callbacks = {}
    const items = []
    let requestSeed = 0
    let dataStore = {}
    return ({
        postCommand: function (command, callback) {
            if (!command.requestId) {
                command.requestId = this.nextRequestId(command)
            }
            if (callback) {
                callbacks[command.requestId] = callback
            }
            window.CodeEngineBridge.postMessage(JSON.stringify(command))
        },
        nextRequestId: function (command) {
            return command.serviceName + "." + command.action + "." + requestSeed++
        },
        dispatchResult: function (result) {
              if (callbacks[result.requestId]) {
                var params = result.params;
                if (typeof(result.params) == 'string'){
                  parmams = JSON.parse(result.params);
                }
                let consumed = callbacks[result.requestId](result.resultCode, params) != false
                if (consumed) {
                  delete callbacks[result.requestId]
                }
                return true
              } else {
                return false
              }
            },
        addItem: function (item) {
            items.push(item)
        },
        getItems: function () {
            return items
        },
        getData(key) {
            return dataStore[key]
        },
        setData(key, value) {
            return dataStore[key] = value
        },
        toast: function (message) {
            var command = {
                serviceName: 'Toast',
                action: 'makeToast',
                params: { "message": message }
            }
            pluginService.postCommand(command, function () { })
        },
        goPrevPageWithData: function () {
            let command = {
                serviceName: 'Navigator',
                action: 'goPrevPageWithData',
            }
            pluginService.postCommand(command, function () { })
        },
        showSoftKeyboard: function (maxInputLength, encType, onDone) {
           var command = {
                serviceName: "Keyboard",
                action: "showSoftKeyboard",
                params: {}
            }
            pluginService.postCommand(command, function (resultCode) {
                console.log("Show soft keyboard Result resultCode: " + resultCode)
            })
        },
        hideSoftKeyboard: function () {
            var command = {
                serviceName: "Keyboard",
                action: "hideSoftKeyboard",
                params: {}
            }
            pluginService.postCommand(command, function (resultCode, result) {
                console.log("Hide soft keyboard Result resultCode: " + resultCode)
            })
        },
    })
})()

function plugin(name, items) {
    pluginService.addItem({ "name": name, "items": items })
}

plugin("00. Non-Plugin Sample", [
    {
        viewType: "link",
        linkText: "Tel '01012345678'",
        linkUrl: "tel:01012345678",
    },
    {
        viewType: "link",
        linkText: "Mail To 'test1234@gmail.com'",
        linkUrl: "mailto:test1234@gmail.com",
    },
    {
        viewType: "button",
        buttonText: "JS Alert (표준)",
        onClick: function () {
            var message = ""
            for(var i = 0; i < 19; i++) {
                message += "Hello (" + i + ")\n"
            }
            message += "Hello (" + i + ")"

            alert(message)
        }
    },
    {
        viewType: "button",
        buttonText: "JS Confirm (표준)",
        onClick: function () {
            confirm("내용 (표준)")
        }
    },
    {
        viewType: "button",
        buttonText: "JS Confirm (JSON)",
        onClick: function () {
            var message = ""
            for(var i = 0; i < 20; i++) {
                message += "Hello (" + i + ")\n"
            }
            var params = {
                "title": "제목 (title)",
                "message": "내용 (message)\n" + message,
                "confirmText": "확인 (confirm)",
                "cancelText": "취소 (cancel)"
            }
            confirm(JSON.stringify(params))
        }
    },
])

plugin("01. User Preference", [
    {
        "viewType": "button",
        "buttonText": "Set Value",
        "onClick": function () {
            var command = {
                serviceName: "UserPref",
                action: "setValue",
                params: {
                    "key_a": "value_a",
                    "key_b": "value_b",
                    "key_c": "value_c",
                    "key_d": "value_d",
                    "key_e": "value_e",
                }
            }
            pluginService.postCommand(command)
        }
    },
    {
        "viewType": "button",
        "buttonText": "Get Value",
        "onClick": function () {
            var command = {
                serviceName: "UserPref",
                action: "getValue",
                params: {
                    keys: ["key_a", "key_b", "key_c", "key_no", "key_d", "key_e"]
                }
            }
            pluginService.postCommand(command)
        }
    },
    {
        "viewType": "button",
        "buttonText": "Remove Value",
        "onClick": function () {
            var command = {
                serviceName: "UserPref",
                action: "removeValue",
                params: {
                    keys: ["key_a", "key_b", "key_c", "key_no"]
                }
            }
            pluginService.postCommand(command)
        }
    },
])

plugin("02. WebViewData", [
    {
        viewType: "button",
        buttonText: "Set Value",
        onClick: function () {
            var command = {
                serviceName: "WebViewData",
                action: "setValue",
                params: {
                    "key1": "value1",
                    "key2": "value2"
                }
            }
            pluginService.postCommand(command)
        },
    },
    {
        viewType: "button",
        buttonText: "Get Value",
        onClick: function () {
            var command = {
                serviceName: "WebViewData",
                action: "getValue",
                params: {
                    "keys": ["key1", "key2", "key3"]
                }
            }
            pluginService.postCommand(command)
        },
    },
    {
        viewType: "button",
        buttonText: "Set JSON Data",
        onClick: function () {
            var command = {
                serviceName: "WebViewData",
                action: "setJsonData",
                params: {
                    "data": {
                        "id": "123456"
                    }
                }
            }
            pluginService.postCommand(command)
        },
    },
    {
        viewType: "button",
        buttonText: "Get JSON Data",
        onClick: function () {
            var command = {
                serviceName: "WebViewData",
                action: "getJsonData",
                params: {
                }
            }
            pluginService.postCommand(command)
        },
    },
])

plugin("03. Location", [
    {
        viewType: "button",
        buttonText: "Get Current Location",
        onClick: function () {
            var command = {
                serviceName: "Location",
                action: "getCurrentLocation",
                params: {}
            }
            pluginService.postCommand(command)
        }
    },
    {
        viewType: "button",
        buttonText: "Is Permission For Location",
        onClick: function () {
            var command = {
                serviceName: "Location",
                action: "isPermissionForLocation",
                params: {}
            }
            pluginService.postCommand(command)
        }
    },
])

plugin("04. Toast", [
    {
        viewType: "button",
        buttonText: "Make Toast",
        onClick: function () {
            var command = {
                serviceName: 'Toast',
                action: 'makeToast',
                params: { message: 'Call Native Toast From Web' }
            }
            pluginService.postCommand(command)
        },
    },
    {
        viewType: "button",
        buttonText: "Make Toast (bottomOffset: 200dp)",
        onClick: function () {
            var command = {
                serviceName: 'Toast',
                action: 'makeToast',
                params: {
                    message: '더 높은 곳에 표시됨',
                    bottomOffset: 200
                }
            }
            pluginService.postCommand(command)
        },
    },
])

plugin("05. WebPopup", [
    {
        viewType: "button",
        buttonText: "open popup",
        onClick: function () {
            var command = {
                serviceName: 'WebPopup',
                action: 'openPopup',
                params: {
                    title: '팝업 제목',
                    url: 'https://www.naver.com',
                    showCloseButton: false,
                    confirmText: "확인",
                    cancelText: "취소"
                }
            }
            pluginService.postCommand(command)
        },
    },
    {
        viewType: "button",
        buttonText: "close popup",
        onClick: function () {
            var command = {
                serviceName: 'WebPopup',
                action: 'closePopup'
            }
            pluginService.postCommand(command)
        },
    },
        {
            viewType: "button",
            buttonText: "open image with zooming",
            onClick: function () {
                var command = {
                    serviceName: 'WebPopup',
                    action: 'openPopup',
                    params: {
                        title: '',
                        url: 'https://media.wired.com/photos/613b8b5f924283bfff0bca16/master/w_1600,c_limit/Gear-How-to-Screenshot-Android.jpg',
                        showCloseButton: true,
                        confirmText: '확인',
                        cancelText: '취소'
                    }
                }
                pluginService.postCommand(command)
            },
        },
])

plugin("06. Keyboard", [
    {
        viewType: "dual_button_and_input",
        buttonText1: "Show Soft Keyboard",
        onClick1: function () {
            var command = {
                serviceName: "Keyboard",
                action: "showSoftKeyboard",
                params: {}
            }
            pluginService.postCommand(command, function (resultCode) {
                console.log("Show soft keyboard Result resultCode: " + resultCode)
            })
        },
        buttonText2: "Hide Soft Keyboard",
        onClick2: function () {
            var command = {
                serviceName: "Keyboard",
                action: "hideSoftKeyboard",
                params: {}
            }
            pluginService.postCommand(command, function (resultCode, result) {
                console.log("Hide soft keyboard Result resultCode: " + resultCode)
            })
        }
    },
])

plugin("07. Cache", [
    {
        "viewType": "button",
        "buttonText": "Delete Cache",
        "onClick": function () {
            var command = {
                serviceName: "Cache",
                action: "deleteCache",
                params: {
                    "allCookie": true,
                    "webViewCache": true,
                    "appCache": true
                }
            }
            pluginService.postCommand(command)
        }
    },
])

plugin("08. SMS", [
    {
                "viewType": "button",
                "buttonText": "SendSMSWithCaptureImage",
                "onClick": function () {
                    var command = {
                        serviceName: "SMS",
                        action: "sendSMSWithCaptureImage",
                        params: {
                            "phoneNum": "01012345678",
                             "message": "안녕하세요. 첫번째 문자 전송 메시지 테스트입니다.",
                              "imageYn": "Y",
                        }
                    }
                    pluginService.postCommand(command)
                }
            },
    {
                "viewType": "button",
                "buttonText": "SendSMSWithCaptureImage imageYn null",
                "onClick": function () {
                    var command = {
                        serviceName: "SMS",
                        action: "sendSMSWithCaptureImage",
                        params: {
                            "phoneNum": "01012345678",
                             "message": "안녕하세요. 두 번째 문자 전송 테스트입니다.",
                        }
                    }
                    pluginService.postCommand(command)
                }
            },
                {
                "viewType": "button",
                "buttonText": "SendSMSWithCaptureImage phoneNum empty",
                "onClick": function () {
                    var command = {
                        serviceName: "SMS",
                        action: "sendSMSWithCaptureImage",
                        params: {
                            "phoneNum": "",
                             "message": "안녕하세요. 캡쳐 이미지 문자 전송 테스트입니다.",
                        }
                    }
                    pluginService.postCommand(command)
                }
            },
                {
                "viewType": "button",
                "buttonText": "SendSMSWithCaptureImage phoneNum multi",
                "onClick": function () {
                    var command = {
                        serviceName: "SMS",
                        action: "sendSMSWithCaptureImage",
                        params: {
                            "phoneNum": "01012345678, 01012345679",
                             "message": "안녕하세요. 테스트 SMS 입니다.",
                             "imageYn": "Y",
                        }
                    }
                    pluginService.postCommand(command)
                }
            },

        {
                    "viewType": "button",
                    "buttonText": "SendSMSWithImage 이미지 없을시",
                    "onClick": function () {
                        var command = {
                            serviceName: "SMS",
                            action: "sendSMS",
                            params: {
                                "phoneNum": "01012345678",
                                 "message": "안녕하세요. 테스트입니다.",
                            }
                        }
                        pluginService.postCommand(command)
                    }
                },
])


var remoteCipherText = ""
plugin("09. SecureData", [
    {
        viewType: "button",
        buttonText: "Encrypt Split",
        onClick: function () {
            var command = {
                serviceName: "SecureData",
                action: "encryptSplit",
                params: {
                    "dataKey": "devTest",
                    "plainText": "분산암호화저장원문(" + Date.now() + ")"
                }
            }
            pluginService.postCommand(command, function (resultCode, params) {
                alert("resultCode: " + resultCode + "\n" + JSON.stringify(params))
                console.debug("[" + params.halfCipherText + "]")
                remoteCipherText = params.halfCipherText
            })
        }
    },
    {
        viewType: "button",
        buttonText: "Decrypt Join",
        onClick: function () {
            var command = {
                serviceName: "SecureData",
                action: "decryptJoin",
                params: {
                    "dataKey": "devTest",
                    "halfCipherText": remoteCipherText
                }
            }
            pluginService.postCommand(command, function (resultCode, params) {
                alert("resultCode: " + resultCode + "\n" + JSON.stringify(params))
            })
        }
    },
    {
        viewType: "button",
        buttonText: "Remove Value",
        onClick: function () {
            var command = {
                serviceName: "SecureData",
                action: "remove",
                params: {
                    "dataKeys": ["devTest", "devTest2"]
                }
            }
            pluginService.postCommand(command, function (resultCode, params) {
                alert("resultCode: " + resultCode + "\n" + JSON.stringify(params))
            })
        }
    },
    {
        viewType: "button",
        buttonText: "Contains Value",
        onClick: function () {
            var command = {
                serviceName: "SecureData",
                action: "contains",
                params: {
                    "dataKeys": ["devTest", "devTest2"]
                }
            }
            pluginService.postCommand(command, function (resultCode, params) {
                alert("resultCode: " + resultCode + "\n" + JSON.stringify(params))
            })
        }
    },

    {
        viewType: "button",
        buttonText: "SetValue Value",
        onClick: function () {
            var command = {
                serviceName: "SecureData",
                action: "setValue",
                params: {
                    "key" : "simpleTransferId",
                    "value" : "12342578908042390124907"
                }
            }
            pluginService.postCommand(command, function (resultCode, params) {
                alert("SecureData setValue resultCode: " + resultCode + "\n" + JSON.stringify(params))
            })
        }
    },

    {
        viewType: "button",
        buttonText: "GetValue Value",
        onClick: function () {
            var command = {
                serviceName: "SecureData",
                action: "getValue",
                params: {
                    "key" : "simpleTransferId"
                }
            }
            pluginService.postCommand(command, function (resultCode, params) {
                alert("SecureData getValue resultCode: " + resultCode + "\n" + JSON.stringify(params))
            })
        }
    },
])

plugin("10. Crypto", [
    {
        viewType: "button",
        buttonText: "encryptAES",
        onClick: function () {
            var command = {
                serviceName: "Crypto",
                action: "encryptAES",
                params: {
                    "PlainText" : {
                        "SAVE_DATE" : "2023.03.10한글됨?aaaa"
                    }
                }
            }
            pluginService.postCommand(command)
        },
    },
    {
        viewType: "button",
        buttonText: "decryptAES",
        onClick: function () {
            var command = {
                serviceName: "Crypto",
                action: "decryptAES",
                params: {
                    "CipherText" : "KFxD37yoyb7wgioXi2PGBiTg21y8Uyyk9qOGnUz0cckzdFF8y9Vhr4550vatjKOx3BDv8DMt6caTegk6c46Ui1NTfLUOkDMnPltyxhr4X+wk3obHbkf6vWoOaKdTMhtg"
                }
            }
            pluginService.postCommand(command)
        },
    },
])

plugin("11. AppInfo", [
    {
        viewType: "button",
        buttonText: "Get Device Info",
        onClick: function () {
            var command = {
                serviceName: "AppInfo",
                action: "getDeviceInfo",
                params: {
                    "needPhoneNo": true,
                }
            }
            pluginService.postCommand(command, function (resultCode, params) {
                var message = "AppInfoPlugin Get Device Info Result\nresultCode: " + resultCode + "\n" + JSON.stringify(params, null, 2)
                alert(message)
                console.log(message)
            })
        },
    },
])

plugin("12. Navigator", [
    {
        viewType: "button",
        buttonText: "load URL",
        onClick: function () {
            var command = {
                serviceName: 'Navigator',
                action: 'loadUrl',
                params: {
                    "url": "file:///android_asset/plugin/plugin2.html",
                }
            }
            pluginService.postCommand(command, function () {
                console.log('loadUrl 완료')
            })
        },
    },
    {
        viewType: "button",
        buttonText: "move with URL",
        onClick: function () {
            var command = {
                serviceName: 'Navigator',
                action: 'moveWithUrl',
                params: {
                    "url": "file:///android_asset/plugin/plugin2.html",
                }
            }
            pluginService.postCommand(command, function () {
                console.log('move with URL 완료')
            })
        },
    },
    {
        viewType: "button",
        buttonText: "move with URL and Data",
        onClick: function () {
            var command = {
                serviceName: 'Navigator',
                action: 'moveWithUrl',
                params: {
                    "url": "file:///android_asset/plugin/plugin2.html",
                    "data": {
                        "key1": "value1",
                        "key2": "value2",
                    },
                }
            }
            pluginService.postCommand(command, function () {
                console.log('move with URL and Data 완료')
            })
        },
    },
    {
        viewType: "button",
        buttonText: "move with URL and Data and Close Button",
        onClick: function () {
            var command = {
                serviceName: 'Navigator',
                action: 'moveWithUrl',
                params: {
                    "url": "file:///android_asset/plugin/plugin2.html",
                    "data": {
                        "key1": "value1",
                        "key2": "value2",
                    },
                    "showCloseButton": true
                }
            }
            pluginService.postCommand(command, function () {
                console.log('move with URL and Data 완료')
            })
        },
    },
    {
        viewType: "button",
        buttonText: "Go Prev Page With Data",
        onClick: function () {
            var command = {
                serviceName: 'Navigator',
                action: 'goPrevPageWithData',
                params: {
                    "key3": "value3",
                    "key4": "value4",
                }
            }
            pluginService.postCommand(command, function () {
                console.log('이전 페이지 가기 완료')
            })
        },
    },
    {
        viewType: "button",
        buttonText: "open with Browser",
        onClick: function () {
            var command = {
                serviceName: 'Navigator',
                action: 'openWithBrowser',
                params: {
                    "url": "http://www.daum.net",
                }
            }
            pluginService.postCommand(command, function () {
                console.log('openWithBrowser 완료')
            })
        },
    },
    {
        viewType: "button",
        buttonText: "close app",
        onClick: function () {
            var command = {
                serviceName: 'Navigator',
                action: 'closeApp',
                params: {}
            }
            pluginService.postCommand(command)
        },
    },
])

plugin("13. ProgressBar", [
    {
        viewType: "button",
        buttonText: "setProgress(true)",
        onClick: function () {
            var command = {
                serviceName: 'ProgressBar',
                action: 'setProgress',
                params: {
                    "isLoading": true,
                }
            }
            pluginService.postCommand(command, function () {})
        },
    },
    {
        viewType: "button",
        buttonText: "setProgress(false)",
        onClick: function () {
            var command = {
                serviceName: 'ProgressBar',
                action: 'setProgress',
                params: {
                    "isLoading": true,
                }
            }
            pluginService.postCommand(command, function () {})
        },
    },
])

plugin("14. Permission", [
    {
        viewType: "button",
        buttonText: "get permissions",
        onClick: function () {
            var command = {
                serviceName: 'Permission',
                action: 'getPermissions',
                params: {}
            }
            pluginService.postCommand(command, function (resultCode, params) {
                alert("resultCode: " + resultCode + "\n" + JSON.stringify(params))
            })
        },
    },
    {
            viewType: "button",
            buttonText: "go App Settings",
            onClick: function () {
                var command = {
                    serviceName: 'Permission',
                    action: 'goAppSettings',
                    params: {}
                }
                pluginService.postCommand(command)
            },
        },
])

plugin("15. Share", [
    {
        viewType: "button",
        buttonText: "requestShared",
        onClick: function () {
            var command = {
                serviceName: "Share",
                action: "sendText",
                params: {
                    "title" : "제목",
                    "text" : "테스트 메시지입니다.",
                    "chooserTitle" : "공유 앱을 선택해주세요"
                }
            }
            pluginService.postCommand(command)
        },
    },
    {
        viewType: "button",
        buttonText: "Share Send Image",
        onClick: function () {
            var command = {
                serviceName: "Share",
                action: "sendImage",
                params: {
                    "imageUrl": "https://media.wired.com/photos/613b8b5f924283bfff0bca16/master/w_1600,c_limit/Gear-How-to-Screenshot-Android.jpg",
                    "chooserTitle" : ""
                }
            }
            pluginService.postCommand(command)
        },
    },
    {
        viewType: "button",
        buttonText: "Share Send Image with Chooser title",
        onClick: function () {
            var command = {
                serviceName: "Share",
                action: "sendImage",
                params: {
                    "imageUrl": "https://e0.pxfuel.com/wallpapers/868/394/desktop-wallpaper-these-star-wars-battlefront-screenshots-are-beautiful-star-wars-star-wars-hoth.jpg",
                    "chooserTitle" : "이미지 공유하기 테스트"
                }
            }
            pluginService.postCommand(command)
        },
    },
])

plugin("16. Barcode", [
    {
        viewType: "button_and_image",
        buttonText: "Create Barcode",
        onClick: function () {
            var command = {
                serviceName: "Barcode",
                action: "viewBarcode",
                params: {
                    "type": "code128",
                    "data": "123-ABC-012"
                }
            }
            pluginService.postCommand(command)
        },
    },
    {
        viewType: "button_and_image",
        buttonText: "Create QR code",
        onClick: function () {
            var command = {
                serviceName: "Barcode",
                action: "viewBarcode",
                params: {
                    "type": "qrcode",
                    "data": "123-ABC-012"
                }
            }
            pluginService.postCommand(command)
        },
    },
])

plugin("17. ImageEdit", [
    {
        viewType: "button_and_image",
        buttonText: "Edit Image From Camera",
        onClick: function () {
            var command = {
                serviceName: "ImageEdit",
                action: "editFromCamera",
                param: {
                    "maxSize": 1572864
                }
            }
            pluginService.postCommand(command)
        },
    },
    {
        viewType: "button_and_image",
        buttonText: "Edit Image From Gallery",
        onClick: function () {
            var command = {
                serviceName: "ImageEdit",
                action: "editFromGallery",
                param: {
                    "maxSize": 1572864
                }
            }
            pluginService.postCommand(command)
        },
    },
])

/*
// ImgLy를 사용하려면 License를 구매해야합니다.
plugin("18. ImgLy", [
    {
        viewType: "button_and_image",
        buttonText: "Edit Image From Camera",
        onClick: function () {
            var command = {
                serviceName: "ImgLy",
                action: "editFromCamera",
            }
            pluginService.postCommand(command)
        },
    },
    {
        viewType: "button_and_image",
        buttonText: "Edit Image From Gallery",
        onClick: function () {
            var command = {
                serviceName: "ImgLy",
                action: "editFromGallery",
            }
            pluginService.postCommand(command)
        },
    },
])
*/

plugin("19. PIP", [
    {
        viewType: "button_and_image",
        buttonText: "Launch PIP window",
        onClick: function () {
            var command = {
                serviceName: "PIP",
                action: "launchPIPWindow",
                params: {
                    "url": "https://appdept.shinsegae.com/shopping/live/player/voqz96oze4r1k70r",
                    // "url": "https://app.ds.shinsegae.com/wallet/deptLiveStream/detail?reservationId=7k45j10q89z2q3ne&toBeLiveYn=L&sectionCd=live&cust_id=&scomBrocMainCd=30",
                    "data": {
                        "key1": "value1",
                        "key2": "value2",
                    },
                }
            }
            pluginService.postCommand(command)
        },
    },
    {
        viewType: "button_and_image",
        buttonText: "Launch PIP window with Close Button",
        onClick: function () {
            var command = {
                serviceName: "PIP",
                action: "launchPIPWindow",
                params: {
                    "url": "file:///android_asset/plugin/plugin2.html",
                    "data": {
                        "key1": "value1",
                        "key2": "value2",
                    },
                    "showCloseButton": true
                }
            }
            pluginService.postCommand(command)
        },
    },
    {
        viewType: "button_and_image",
        buttonText: "minimize window",
        onClick: function () {
            var command = {
                serviceName: "PIP",
                action: "minimize",
            }
            pluginService.postCommand(command)
        },
    },
])

plugin("20. Clipboard", [
    {
        viewType: "button",
        buttonText: "Set Clipboard Text",
        onClick: function () {
            var command = {
                "serviceName": "Clipboard",
                "action": "setClipboardText",
                "params": {
                    "copyText": "이 메시지를 복사해주세요!",
                }
            }
            pluginService.postCommand(command)
        }
    },
    {
        viewType: "button",
        buttonText: "Get Clipboard Text",
        onClick: function () {
            var command = {
                "serviceName": "Clipboard",
                "action": "getClipboardText",
                "params": {}
            }
            pluginService.postCommand(command)
        },
    },
])

plugin("21. JWTAuth", [
    {
        viewType: "button",
        buttonText: "Login",
        onClick: function () {
            var command = {
                "serviceName": "JWTAuth",
                "action": "login",
                "params": {
                    "id": "test@gmail.com",
                    "password": "123Test",
                }
            }
            pluginService.postCommand(command)
        }
    },
    {
        viewType: "button",
        buttonText: "Logout",
        onClick: function () {
            var command = {
                "serviceName": "JWTAuth",
                "action": "logout",
            }
            pluginService.postCommand(command)
        }
    },
    {
        viewType: "button",
        buttonText: "Get token",
        onClick: function () {
            var command = {
                "serviceName": "JWTAuth",
                "action": "getToken",
            }
            pluginService.postCommand(command)
        }
    },
    {
        viewType: "button",
        buttonText: "Update token",
        onClick: function () {
            var command = {
                "serviceName": "JWTAuth",
                "action": "updateToken",
                "params": {
                    "token": "eyJ......",
                }
            }
            pluginService.postCommand(command)
        }
    },
])

plugin("22. ImageViewer", [
    {
        viewType: "button",
        buttonText: "open image with zooming",
        onClick: function () {
            var command = {
                serviceName: 'ImageViewer',
                action: 'openPopup',
                params: {
                    title: '',
                    url: 'https://media.wired.com/photos/613b8b5f924283bfff0bca16/master/w_1600,c_limit/Gear-How-to-Screenshot-Android.jpg',
                    confirmText: '확인',
                    cancelText: '취소'
                }
            }
            pluginService.postCommand(command)
        },
    },
    {
        viewType: "button",
        buttonText: "close popup",
        onClick: function () {
            var command = {
                serviceName: 'ImageViewer',
                action: 'closePopup'
            }
            pluginService.postCommand(command)
        },
    },
])

plugin("23. Fingerprint", [
    {
        viewType: "button",
        buttonText: "show Fingerprint Prompt",
        onClick: function () {
            var command = {
                serviceName: 'Fingerprint',
                action: 'showFingerprintPrompt'
            }
            pluginService.postCommand(command)
        },
    },
    {
        viewType: "button",
        buttonText: "cancel Fingerprint Prompt",
        onClick: function () {
            var command = {
                serviceName: 'Fingerprint',
                action: 'cancelFingerprintPrompt'
            }
            pluginService.postCommand(command)
        },
    },
])

plugin("24. FileSelect", [
    {
        viewType: "button",
        buttonText: "select a file with mime-type",
        onClick: function () {
            var command = {
                serviceName: 'FileSelect',
                action: 'selectWithMimeTypes',
                params: {
                    mimeTypes: ["application/pdf", "image/*"], // pdf와 image 파일을 선택할 수 있게 함.
                    multiple: false
                }
            }
            pluginService.postCommand(command)
        },
    },
        {
            viewType: "button",
            buttonText: "select files with mime-type",
            onClick: function () {
                var command = {
                    serviceName: 'FileSelect',
                    action: 'selectWithMimeTypes',
                    params: {
                        mimeTypes: ["application/pdf", "image/*"], // pdf와 image 파일을 선택할 수 있게 함.
                        multiple: true
                    }
                }
                pluginService.postCommand(command)
            },
        },
])

plugin("28. BarcodeDetectPlugin", [
    {
        viewType: "button_and_image",
        buttonText: "Scan Barcode/QR code",
        onClick: function () {
            var command = {
                serviceName: "BarcodeDetect",
                action: "scan",
            }
            pluginService.postCommand(command)
        },
    },
])

plugin("29. NfcTagPlugin", [
    {
        viewType: "button",
        buttonText: "Read NFC Tag",
        onClick: function () {
            var command = {
                serviceName: "NfcTag",
                action: "read",
            }
            pluginService.postCommand(command)
        },
    },
])

plugin("30. Network", [
    {
        viewType: "button_and_image",
        buttonText: "getStatus",
        onClick: function () {
            var command = {
                serviceName: "Network",
                action: "getStatus",
            }
            pluginService.postCommand(command)
        },
    },
])

plugin("31. SmsReceiver", [
    {
        viewType: "button_and_image",
        buttonText: "register",
        onClick: function () {
            var command = {
                serviceName: "SmsReceiver",
                action: "register",
            }
            pluginService.postCommand(command)
        },
    },
    {
        viewType: "button_and_image",
        buttonText: "unregister",
        onClick: function () {
            var command = {
                serviceName: "SmsReceiver",
                action: "unregister",
            }
            pluginService.postCommand(command)
        },
    },
])


