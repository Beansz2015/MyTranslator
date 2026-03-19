package com.example.mytranslator

data class LangOption(val name: String, val locale: String, val voice: String)

val LANGUAGES = listOf(
    LangOption("English",             "en-US",  "en-US-JennyNeural"),
    LangOption("Malay",               "ms-MY",  "ms-MY-YasminNeural"),
    LangOption("Chinese (Mandarin)",  "zh-CN",  "zh-CN-XiaoxiaoNeural"),
    LangOption("Hindi",               "hi-IN",  "hi-IN-SwaraNeural"),
    LangOption("Bengali",             "bn-IN",  "bn-IN-TanishaaNeural"),
    LangOption("Tamil",               "ta-IN",  "ta-IN-PallaviNeural"),
    LangOption("Chinese (Cantonese)", "zh-HK",  "zh-HK-HiuMaanNeural"),
    LangOption("Japanese",            "ja-JP",  "ja-JP-NanamiNeural"),
    LangOption("Filipino",            "fil-PH", "fil-PH-BlessicaNeural"),
    LangOption("Korean",              "ko-KR",  "ko-KR-SunHiNeural"),
    LangOption("Thai",                "th-TH",  "th-TH-PremwadeeNeural"),
    LangOption("French",              "fr-FR",  "fr-FR-DeniseNeural"),
    LangOption("German",              "de-DE",  "de-DE-KatjaNeural"),
    LangOption("Arabic",              "ar-SA",  "ar-SA-ZariyahNeural"),
    LangOption("Russian",             "ru-RU",  "ru-RU-SvetlanaNeural"),
    LangOption("Spanish",             "es-ES",  "es-ES-ElviraNeural"),
    LangOption("Vietnamese",          "vi-VN",  "vi-VN-HoaiMyNeural"),
    LangOption("Burmese",             "my-MM",  "my-MM-NilarNeural"),
    LangOption("Lao",                 "lo-LA",  "lo-LA-KeomanyNeural"),
    LangOption("Nepali",              "ne-NP",  "ne-NP-HemkalaNeural"),
    LangOption("Khmer",               "km-KH",  "km-KH-SreymomNeural"),
    LangOption("Ukrainian",           "uk-UA",  "uk-UA-PolinaNeural")
)

enum class AppState { IDLE, LISTENING, SPEAKING }
