package com.xiguli.langhuan

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.os.SystemClock
import android.view.View
import android.view.animation.PathInterpolator
import com.xiguli.langhuan.data.local.StartupDatabaseStatus
import com.xiguli.langhuan.data.local.StartupDatabaseGate
import com.xiguli.langhuan.engine.PostStartupInitializer
import com.xiguli.langhuan.ui.LanghuanRootV4
import com.xiguli.langhuan.ui.ExternalBookImportCoordinatorV1
import com.xiguli.langhuan.ui.StudioViewModel
import com.xiguli.langhuan.ui.theme.LanghuanStableTheme
import com.xiguli.langhuan.ui.theme.LanghuanThemeModeStateV50

class MainActivity : ComponentActivity() {
    private val externalBooks by lazy { ViewModelProvider(this)[ExternalBookImportCoordinatorV1::class.java] }

    /** V95: the launch screen stays up while the startup database check runs (bounded). */
    @Volatile private var startupChecking = true

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate: swaps Theme.Langhuan.Starting for Theme.Langhuan.
        val splash = installSplashScreen()
        val splashStartedAt = SystemClock.uptimeMillis()
        super.onCreate(savedInstanceState)
        // Hold the mark (never a blank frame) until the first real screen can draw, but never
        // longer than SPLASH_MAX_HOLD_MS_V95: a slow migration then shows the checking screen.
        splash.setKeepOnScreenCondition {
            startupChecking && SystemClock.uptimeMillis() - splashStartedAt < SPLASH_MAX_HOLD_MS_V95
        }
        // Smooth hand-off: the mark lifts and fades while the splash surface dissolves into the
        // already-drawn first screen (same paper colour, so there is no white flash).
        splash.setOnExitAnimationListener { provider ->
            val ease = PathInterpolator(0.2f, 0f, 0f, 1f)
            val icon: View? = runCatching { provider.iconView }.getOrNull()
            icon?.let { mark ->
                mark.animate().alpha(0f).scaleX(1.08f).scaleY(1.08f).translationY(-mark.height * .04f)
                    .setDuration(SPLASH_EXIT_MS_V95).setInterpolator(ease).start()
            }
            provider.view.animate().alpha(0f).setStartDelay(60).setDuration(SPLASH_EXIT_MS_V95)
                .setInterpolator(ease).withEndAction { provider.remove() }.start()
        }
        val incoming = intent
        // Preserve the Activity's launch identity for Android lifecycle/result tracking.
        // Clear untrusted default arguments before the SavedStateHandle ViewModel is created.
        setIntent(Intent(incoming ?: Intent(this, MainActivity::class.java)).apply {
            replaceExtras(null as Bundle?)
            clipData = null
        })
        // Restored requests already live in the validated saved queue. Never replay a launch.
        if (savedInstanceState == null) receiveExternalBook(incoming)
        enableEdgeToEdge()
        setContent {
            // Keep the proven launcher path plain and dependency-light until Room is healthy.
            MaterialTheme {
                StartupDatabaseRoot(externalBooks, onStartupSettled = { startupChecking = false })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Each new delivery is consumed directly. getIntent retains this Activity's launch
        // identity; changing it here would also break lifecycle tracking across recreation.
        receiveExternalBook(intent)
    }

    private fun receiveExternalBook(incoming: Intent?) {
        if (incoming?.action != Intent.ACTION_VIEW && incoming?.action != Intent.ACTION_SEND) return
        externalBooks.receive(requireNotNull(incoming))
    }
}

private const val SPLASH_MAX_HOLD_MS_V95 = 2_500L
private const val SPLASH_EXIT_MS_V95 = 280L

private sealed class LauncherState {
    data object Checking : LauncherState()
    data class Ready(val status: StartupDatabaseStatus) : LauncherState()
    data class Failed(val status: StartupDatabaseStatus) : LauncherState()
}

@Composable
private fun StartupDatabaseRoot(
    externalBooks: ExternalBookImportCoordinatorV1,
    onStartupSettled: () -> Unit = {},
) {
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
        // Read the persisted theme once before the first themed frame, not on every
        // recomposition of the launcher root.
        if (status.ready) LanghuanThemeModeStateV50.init(context)
        launcherState = if (status.ready) LauncherState.Ready(status) else LauncherState.Failed(status)
        onStartupSettled()
    }

    when (val state = launcherState) {
        LauncherState.Checking -> LauncherCheckingScreen()
        is LauncherState.Failed -> LauncherFailureScreen(state.status)
        is LauncherState.Ready -> {
            // Visual styling and noncritical background work begin only after startup is proven safe.
            LanghuanStableTheme(themeMode = LanghuanThemeModeStateV50.current) {
                LaunchedEffect(Unit) { PostStartupInitializer.start(context) }
                val studioViewModel: StudioViewModel = viewModel()
                LanghuanRootV4(studioViewModel, externalBooks)
            }
        }
    }
}

@Composable
private fun LauncherCheckingScreen() {
    // Same paper surface and mark as the launch screen, so a slow check continues it seamlessly.
    Surface(Modifier.fillMaxSize(), color = colorResource(R.color.splash_paper_v96)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(28.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Same 288 dp canvas as the system splash icon, nudged so the sprig stays exactly where
            // the launch screen drew it while the progress line appears underneath.
            Spacer(Modifier.height(40.dp))
            Image(
                painter = painterResource(R.drawable.splash_bamboo_v96),
                contentDescription = null,
                modifier = Modifier.size(288.dp),
            )
            LinearProgressIndicator(
                modifier = Modifier.padding(top = 4.dp).width(96.dp).height(2.dp),
                color = colorResource(R.color.splash_ink_v96),
                trackColor = colorResource(R.color.splash_ink_v96).copy(alpha = .16f),
            )
            Text(
                text = "正在检查琅嬛数据…",
                modifier = Modifier.padding(top = 14.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = colorResource(R.color.splash_ink_v96).copy(alpha = .72f),
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
                .safeDrawingPadding()
                // Long SQLite errors and backup paths must stay readable on small screens.
                .verticalScroll(rememberScrollState())
                .padding(28.dp)
                .semantics { liveRegion = LiveRegionMode.Assertive },
            verticalArrangement = Arrangement.Center,
        ) {
            Text("琅嬛启动诊断", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "数据库已经被启动保护拦截，应用没有继续加载可能导致闪退的组件。",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
            // Selectable so the diagnosis and backup path can be copied into a bug report.
            SelectionContainer {
                Column {
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
    }
}
