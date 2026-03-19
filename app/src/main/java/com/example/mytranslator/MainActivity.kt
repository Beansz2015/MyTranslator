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
        translatorManager.stop()
    }
}
