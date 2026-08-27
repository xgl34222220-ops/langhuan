package com.xiguli.langhuan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.ui.LanghuanRoot
import com.xiguli.langhuan.ui.StudioViewModel
import com.xiguli.langhuan.ui.theme.LanghuanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LanghuanTheme {
                val studioViewModel: StudioViewModel = viewModel()
                LanghuanRoot(studioViewModel)
            }
        }
    }
}
