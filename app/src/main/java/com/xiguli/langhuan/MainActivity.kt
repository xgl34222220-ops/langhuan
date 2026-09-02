package com.xiguli.langhuan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiguli.langhuan.data.local.StartupDatabaseStatus
import com.xiguli.langhuan.data.local.StartupDatabaseGate
import com.xiguli.langhuan.engine.PostStartupInitializer
import com.xiguli.langhuan.ui.LanghuanRootV3
import com.xiguli.langhuan.ui.StudioViewModel
import com.xiguli.langhuan.ui.theme.LanghuanStableTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Keep the proven launcher path plain and dependency-light until Room is healthy.
            MaterialTheme {
                StartupDatabaseRoot()
            }
        }
    }
}

private sealed class LauncherState {
    data object Checking : LauncherState()
    data class Ready(val status: StartupDatabaseStatus) : LauncherState()
    data class Failed(val status: StartupDatabaseStatus) : LauncherState()
}

@Composable
private fun StartupDatabaseRoot() {
    val context = LocalContext.current.applicationContext
    var launcherState by remember { mutableStateOf<LauncherState>(LauncherState.Checking) }

    LaunchedEffect(Unit) {
        val status = runCatching { StartupDatabaseGate.prepare(context) }
            .getOrElse { error ->
                StartupDatabaseStatus(
                    ready = false,
                    error = error.message ?: error::class.java.simpleName,
                )
            }
        launcherState = if (status.ready) LauncherState.Ready(status) else LauncherState.Failed(status)
    }

    when (val state = launcherState) {
        LauncherState.Checking -> LauncherCheckingScreen()
        is LauncherState.Failed -> LauncherFailureScreen(state.status)
        is LauncherState.Ready -> {
            // Visual styling and noncritical background work begin only after startup is proven safe.
            LanghuanStableTheme {
                LaunchedEffect(Unit) { PostStartupInitializer.start(context) }
                val studioViewModel: StudioViewModel = viewModel()
                LanghuanRootV3(studioViewModel)
            }
        }
    }
}

@Composable
private fun LauncherCheckingScreen() {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text(
                text = "正在检查琅嬛数据…",
                modifier = Modifier.padding(top = 18.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun LauncherFailureScreen(status: StartupDatabaseStatus) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("琅嬛启动诊断", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "数据库已经被启动保护拦截，应用没有继续加载可能导致闪退的组件。",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = status.error.ifBlank { "未知数据库错误" },
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (status.backupPath.isNotBlank()) {
                Text(
                    text = "旧数据库备份：${status.backupPath}",
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
