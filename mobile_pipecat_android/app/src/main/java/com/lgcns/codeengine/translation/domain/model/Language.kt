package com.lgcns.codeengine.translation.domain.model

data class Language(
    val code: String,
    val name: String,
    val nativeName: String,
    val countryCode: String
) {
    companion object {
        val AUTO = Language("auto", "Auto Detect", "Auto Detect", "🌐")

        val SUPPORTED_OUTPUT_LANGUAGES = listOf(
            Language("en", "English", "English", "US"),
            Language("ko", "Korean", "한국어", "KR"),
            Language("ja", "Japanese", "日本語", "JP"),
            Language("zh", "Chinese", "中文", "CN"),
            Language("es", "Spanish", "Español", "ES"),
            Language("fr", "French", "Français", "FR"),
            Language("de", "German", "Deutsch", "DE"),
            Language("it", "Italian", "Italiano", "IT"),
            Language("pt", "Portuguese", "Português", "PT"),
            Language("ru", "Russian", "Русский", "RU"),
            Language("ar", "Arabic", "العربية", "SA"),
            Language("hi", "Hindi", "हिन्दी", "IN"),
            Language("th", "Thai", "ไทย", "TH"),
            Language("vi", "Vietnamese", "Tiếng Việt", "VN"),
            Language("id", "Indonesian", "Bahasa Indonesia", "ID"),
            Language("tr", "Turkish", "Türkçe", "TR"),
            Language("pl", "Polish", "Polski", "PL"),
            Language("nl", "Dutch", "Nederlands", "NL"),
            Language("uk", "Ukrainian", "Українська", "UA"),
            Language("cs", "Czech", "Čeština", "CZ")
        )

        fun findByCode(code: String): Language? {
            return SUPPORTED_OUTPUT_LANGUAGES.find { it.code == code }
        }
    }
}

