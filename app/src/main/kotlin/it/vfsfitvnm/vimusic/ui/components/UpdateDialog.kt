package it.vfsfitvnm.vimusic.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.vimusic.BuildConfig
import it.vfsfitvnm.vimusic.ui.components.themed.DefaultDialog
import it.vfsfitvnm.vimusic.ui.components.themed.DialogTextButton
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.utils.UpdateInfo
import it.vfsfitvnm.vimusic.utils.Updater
import it.vfsfitvnm.vimusic.utils.medium
import it.vfsfitvnm.vimusic.utils.secondary
import it.vfsfitvnm.vimusic.utils.semiBold
import kotlinx.coroutines.launch

private sealed interface UpdateState {
    object Available : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    object Installing : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Prompts the user about a newer release, then downloads and hands the APK to the
 * system installer when they tap Update.
 */
@Composable
fun UpdateDialog(
    info: UpdateInfo,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (colorPalette, typography) = LocalAppearance.current

    var state by remember { mutableStateOf<UpdateState>(UpdateState.Available) }

    val downloading = state is UpdateState.Downloading

    DefaultDialog(
        onDismiss = { if (!downloading) onDismiss() }
    ) {
        BasicText(
            text = "Update available",
            style = typography.s.semiBold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        BasicText(
            text = "v${BuildConfig.VERSION_NAME}  →  v${info.versionName}",
            style = typography.xs.semiBold.copy(color = colorPalette.accent),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (info.changelog.isNotBlank()) {
            BasicText(
                text = info.changelog,
                style = typography.xxs.medium.secondary,
                modifier = Modifier
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 12.dp)
            )
        }

        when (val current = state) {
            is UpdateState.Downloading -> BasicText(
                text = if (current.percent > 0) "Downloading… ${current.percent}%" else "Downloading…",
                style = typography.xs.medium,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            is UpdateState.Installing -> BasicText(
                text = "Opening installer…",
                style = typography.xs.medium,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            is UpdateState.Failed -> BasicText(
                text = current.message,
                style = typography.xs.medium.copy(
                    color = colorPalette.red,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            UpdateState.Available -> Unit
        }

        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            if (!downloading) {
                DialogTextButton(
                    text = if (state is UpdateState.Failed) "Close" else "Later",
                    onClick = onDismiss
                )
            }

            DialogTextButton(
                primary = true,
                enabled = !downloading && state !is UpdateState.Installing,
                text = when (val s = state) {
                    is UpdateState.Downloading -> "${s.percent}%"
                    is UpdateState.Installing -> "Installing…"
                    is UpdateState.Failed -> "Retry"
                    UpdateState.Available -> "Update"
                },
                onClick = {
                    if (downloading || state is UpdateState.Installing) return@DialogTextButton
                    state = UpdateState.Downloading(0)
                    scope.launch {
                        val apk = Updater.download(context, info) { percent ->
                            state = UpdateState.Downloading(percent)
                        }
                        if (apk == null) {
                            state = UpdateState.Failed("Download failed. Check your connection and try again.")
                            return@launch
                        }
                        state = UpdateState.Installing
                        val launched = Updater.install(context, apk)
                        if (!launched) {
                            // The install permission page was opened; keep the APK for when
                            // the user returns. Re-tapping Update will retry the installer.
                            state = UpdateState.Failed("Allow \"Install unknown apps\" for Tempo, then tap Retry.")
                            return@launch
                        }
                        onDismiss()
                    }
                }
            )
        }

        BasicText(
            text = "Tempo installs updates it downloads from its GitHub releases.",
            style = typography.xxs.secondary.copy(textAlign = TextAlign.Center),
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
        )
    }
}
