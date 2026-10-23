package it.vfsfitvnm.vimusic.ui.screens.settings

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import android.content.Intent
import it.vfsfitvnm.vimusic.BuildConfig
import it.vfsfitvnm.vimusic.LocalPlayerAwareWindowInsets
import it.vfsfitvnm.vimusic.ui.components.UpdateDialog
import it.vfsfitvnm.vimusic.ui.components.themed.Header
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.utils.AppLog
import it.vfsfitvnm.vimusic.utils.UpdateInfo
import it.vfsfitvnm.vimusic.utils.Updater
import it.vfsfitvnm.vimusic.utils.secondary
import it.vfsfitvnm.vimusic.utils.toast
import kotlinx.coroutines.launch

@ExperimentalAnimationApi
@Composable
fun About() {
    val (colorPalette, typography) = LocalAppearance.current
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isCheckingForUpdates by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }

    Column(
        modifier = Modifier
            .background(colorPalette.background0)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                    .asPaddingValues()
            )
    ) {
        Header(title = "About") {
            BasicText(
                text = "v${BuildConfig.VERSION_NAME} by 4jeel-cloud",
                style = typography.s.secondary
            )
        }

        SettingsEntryGroupText(title = "SOCIAL")

        SettingsEntry(
            title = "GitHub",
            text = "View the source code",
            onClick = {
                uriHandler.openUri("https://github.com/4jeel-cloud/tempo-music-app")
            }
        )

        SettingsGroupSpacer()

        SettingsEntryGroupText(title = "UPDATES")

        SettingsEntry(
            title = "Check for updates",
            text = when {
                isCheckingForUpdates -> "Checking…"
                availableUpdate != null -> "Version ${availableUpdate?.versionName} is available"
                else -> "Current: v${BuildConfig.VERSION_NAME}"
            },
            isEnabled = !isCheckingForUpdates,
            onClick = {
                isCheckingForUpdates = true
                scope.launch {
                    Updater.checkForUpdatesResult()
                        .onSuccess { info ->
                            isCheckingForUpdates = false
                            if (info != null) {
                                availableUpdate = info
                            } else {
                                context.toast("You're on the latest version")
                            }
                        }
                        .onFailure {
                            isCheckingForUpdates = false
                            context.toast("Couldn't check for updates")
                        }
                }
            }
        )

        SettingsGroupSpacer()

        SettingsEntryGroupText(title = "TROUBLESHOOTING")

        SettingsEntry(
            title = "Share logs",
            text = "Attach recent diagnostics to a report",
            onClick = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Tempo logs (v${BuildConfig.VERSION_NAME})")
                    putExtra(Intent.EXTRA_TEXT, AppLog.buildReport())
                }
                context.startActivity(Intent.createChooser(intent, "Share logs"))
            }
        )

        SettingsEntry(
            title = "Clear logs",
            text = "Delete the in-app diagnostic buffer",
            onClick = {
                AppLog.clear()
                context.toast("Logs cleared")
            }
        )

        SettingsEntry(
            title = "Report an issue",
            text = "You will be redirected to GitHub",
            onClick = {
                uriHandler.openUri("https://github.com/4jeel-cloud/tempo-music-app/issues/new?assignees=&labels=bug&template=bug_report.yaml")
            }
        )

        SettingsEntry(
            title = "Request a feature or suggest an idea",
            text = "You will be redirected to GitHub",
            onClick = {
                uriHandler.openUri("https://github.com/4jeel-cloud/tempo-music-app/issues/new?assignees=&labels=enhancement&template=feature_request.yaml")
            }
        )

        availableUpdate?.let { info ->
            UpdateDialog(
                info = info,
                onDismiss = { availableUpdate = null }
            )
        }
    }
}
