package com.lgcns.codeengine.web.params

// back 이외에 resume, pause 추가할 경우를 대비하여 enum class 관리
enum class JSEventType(val type: String) {

    // Navigate
    BACK("back"),

    // Screen
    RESUME("resume"),
    PAUSE("pause"),

    // Data
    DATA_READY("dataReady"),

    // Motion
    SHAKE("shake"),

    // Push
    MESSAGE("message"),

    // Deeplink param
    MOVE("move"),

    // SMS
    AUTH_NUM("auth_num"),

}