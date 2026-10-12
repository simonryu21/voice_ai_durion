package com.lgcns.codeengine.util

/**
 * 로컬 개발용 loopback/에뮬레이터 호스트 URL인지 여부를 반환한다.
 *
 * 대상 호스트: localhost, 127.0.0.1, 10.0.2.2(Android 에뮬레이터 → 호스트 alias),
 * 0.0.0.0, [::1].
 *
 * 다음 두 가지 문제를 함께 해결하기 위해 사용한다.
 * - `PatternsCompat.WEB_URL`이 TLD 없는 `localhost`를 URL로 인정하지 않는 문제
 * - 로컬 dev 서버(예: Vite 5174) URL을 외부 링크로 오인해 HandoffWebViewActivity로
 *   여는("BACK TO AI") 문제
 */
fun isLocalDevUrl(url: String): Boolean =
    LOCAL_DEV_URL_REGEX.matches(url.trim())

private val LOCAL_DEV_URL_REGEX = Regex(
    "^https?://(localhost|127\\.0\\.0\\.1|10\\.0\\.2\\.2|0\\.0\\.0\\.0|\\[::1\\])(:\\d+)?(/.*)?$",
    RegexOption.IGNORE_CASE,
)
