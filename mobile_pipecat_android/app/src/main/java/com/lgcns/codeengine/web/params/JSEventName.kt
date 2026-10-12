package com.lgcns.codeengine.web.params


// Navigate 이외에 추가할 경우를 대비하여 enum class 관리
enum class JSEventName(val type: String) {
    NAVIGATE("Navigate"),
    DATA("Data"),
    SCREEN("Screen"),
    MOTION("Motion"),
    PUSH("Push"),
    DEEPLINK("Deeplink"),
    SMS("SMS"),
}