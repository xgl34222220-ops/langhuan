package com.xiguli.langhuan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.engine.OriginalCanonIndexCoordinator
import com.xiguli.langhuan.ui.LanghuanRootV3
import com.xiguli.langhuan.ui.StudioViewModel
import com.xiguli.langhuan.ui.theme.LanghuanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Startup-safe v3: secondary indexing must never be allowed to kill the launcher path.
        runCatching { OriginalCanonIndexCoordinator.start(applicationContext) }

        enableEdgeToEdge()
        setContent {
            LanghuanTheme {
                StartupSafeRoot()
            }
        }
    }
}

@Composable
private fun StartupSafeRoot() {
    var retryKey by remember { mutableIntStateOf(0) }

    // Keep startup failures inside the composition so a single eager ViewModel/database
    // initialization problem no longer terminates the whole process on real devices.
    try {
        retryKey // read to force a fresh composition when Retry is pressed
        val studioViewModel: StudioViewModel = viewModel(key = "studio-$retryKey")
        LanghuanRootV3(studioViewModel)
    } catch (error: Throwable) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("琅嬛启动保护已拦截异常", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = error.message ?: error::class.java.simpleName,
                    modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { retryKey += 1 }) {
                    Text("重新尝试")
                }
            }
        }
    }
}
