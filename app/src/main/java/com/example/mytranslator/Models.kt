package com.example.mytranslator

data class LangOption(val name: String, val locale: String, val voice: String)

val LANGUAGES = listOf(
    // en-US-AriaNeural: top-rated conversational female, warm and natural
    LangOption("English",             "en-US",  "en-US-AriaNeural"),
    // ms-MY-YasminNeural: only female option, quality improved in Feb 2025 update
    LangOption("Malay",               "ms-MY",  "ms-MY-YasminNeural"),
    // zh-CN-XiaoxiaoNeural: consistently rated #1 Mandarin voice, highly expressive
    LangOption("Chinese (Mandarin)",  "zh-CN",  "zh-CN-XiaoxiaoNeural"),
    // hi-IN-AartiNeural: GA super-realistic Indian voice, best reviewed for Hindi
    LangOption("Hindi",               "hi-IN",  "hi-IN-AartiNeural"),
    // bn-IN-TanishaaNeural: only female option available
    LangOption("Bengali",             "bn-IN",  "bn-IN-TanishaaNeural"),
    // ta-MY-KaniNeural: Malaysian Tamil accent, most relevant for Penang context
    LangOption("Tamil",               "ta-MY",  "ta-MY-KaniNeural"),
    // zh-HK-HiuMaanNeural: best Cantonese voice, quality improved in Feb 2025 update
    LangOption("Chinese (Cantonese)", "zh-HK",  "zh-HK-HiuMaanNeural"),
    // ja-JP-NanamiNeural: top-rated Japanese female voice, natural and expressive
    LangOption("Japanese",            "ja-JP",  "ja-JP-NanamiNeural"),
    // fil-PH-BlessicaNeural: only female option available
    LangOption("Filipino",            "fil-PH", "fil-PH-BlessicaNeural"),
    // ko-KR-SunHiNeural: most widely used and best reviewed Korean female voice
    LangOption("Korean",              "ko-KR",  "ko-KR-SunHiNeural"),
    // th-TH-PremwadeeNeural: top-rated Thai voice, warm and clear
    LangOption("Thai",                "th-TH",  "th-TH-PremwadeeNeural"),
    // fr-FR-DeniseNeural: consistently top-ranked French female voice
    LangOption("French",              "fr-FR",  "fr-FR-DeniseNeural"),
    // de-DE-KatjaNeural: top-rated German female voice, clear and natural
    LangOption("German",              "de-DE",  "de-DE-KatjaNeural"),
    // ar-EG-SalmaNeural: Egyptian Arabic, most widely understood Arabic dialect
    LangOption("Arabic",              "ar-EG",  "ar-EG-SalmaNeural"),
    // ru-RU-SvetlanaNeural: quality improved in Feb 2025 update, top female Russian
    LangOption("Russian",             "ru-RU",  "ru-RU-SvetlanaNeural"),
    // es-ES-ElviraNeural: best reviewed Spanish female voice
    LangOption("Spanish",             "es-ES",  "es-ES-ElviraNeural"),
    // vi-VN-HoaiMyNeural: quality improved in Feb 2025 update, top Vietnamese voice
    LangOption("Vietnamese",          "vi-VN",  "vi-VN-HoaiMyNeural"),
    // my-MM-NilarNeural: only female option available for Burmese
    LangOption("Burmese",             "my-MM",  "my-MM-NilarNeural"),
    // lo-LA-KeomanyNeural: only female option available for Lao
    LangOption("Lao",                 "lo-LA",  "lo-LA-KeomanyNeural"),
    // ne-NP-HemkalaNeural: only female option available for Nepali
    LangOption("Nepali",              "ne-NP",  "ne-NP-HemkalaNeural"),
    // km-KH-SreymomNeural: only female option available for Khmer
    LangOption("Khmer",               "km-KH",  "km-KH-SreymomNeural"),
    // uk-UA-PolinaNeural: top-rated Ukrainian female voice
    LangOption("Ukrainian",           "uk-UA",  "uk-UA-PolinaNeural")
)

enum class AppState { IDLE, LISTENING, SPEAKING }
