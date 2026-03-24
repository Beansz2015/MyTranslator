package com.example.mytranslator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.mytranslator.ui.theme.MyTranslatorTheme

class MainActivity : ComponentActivity() {

    private val translatorManager = TranslatorManager()

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* handled reactively via hasMicPermission() in UI */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Pre-warm the WebSocket connection immediately on launch
        val defaultLangA     = LANGUAGES.first { it.locale == "en-US" }
        val defaultLangB     = LANGUAGES.first { it.locale == "zh-CN" }
        val defaultShortlist = ShortlistPrefs.load(this)
        translatorManager.preWarm(defaultLangA, defaultLangB, defaultShortlist)

        setContent {
            MyTranslatorTheme {
                TranslatorScreen(
                    translatorManager   = translatorManager,
                    onRequestPermission = {
                        requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    hasMicPermission = {
                        ContextCompat.checkSelfPermission(
                            this, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        translatorManager.destroy()   // full teardown only here
    }
}
