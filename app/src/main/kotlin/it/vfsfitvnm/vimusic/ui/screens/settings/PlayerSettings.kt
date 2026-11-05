package it.vfsfitvnm.vimusic.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.vimusic.LocalPlayerAwareWindowInsets
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.ui.components.SeekBar
import it.vfsfitvnm.vimusic.ui.components.themed.DefaultDialog
import it.vfsfitvnm.vimusic.ui.components.themed.DialogTextButton
import it.vfsfitvnm.vimusic.ui.components.themed.Header
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.utils.isAtLeastAndroid6
import it.vfsfitvnm.vimusic.utils.persistentQueueKey
import it.vfsfitvnm.vimusic.utils.rememberPreference
import it.vfsfitvnm.vimusic.utils.resumePlaybackWhenDeviceConnectedKey
import it.vfsfitvnm.vimusic.utils.secondary
import it.vfsfitvnm.vimusic.utils.semiBold
import it.vfsfitvnm.vimusic.utils.skipSilenceKey
import it.vfsfitvnm.vimusic.utils.swipeSensitivityKey
import it.vfsfitvnm.vimusic.utils.swipeToChangeSongKey
import it.vfsfitvnm.vimusic.utils.toast
import it.vfsfitvnm.vimusic.utils.volumeNormalizationKey

@ExperimentalAnimationApi
@Composable
fun PlayerSettings() {
    val context = LocalContext.current
    val (colorPalette) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current

    var persistentQueue by rememberPreference(persistentQueueKey, true)
    var resumePlaybackWhenDeviceConnected by rememberPreference(
        resumePlaybackWhenDeviceConnectedKey,
        false
    )
    var skipSilence by rememberPreference(skipSilenceKey, false)
    var volumeNormalization by rememberPreference(volumeNormalizationKey, false)
    var swipeToChangeSong by rememberPreference(swipeToChangeSongKey, true)
    var swipeSensitivity by rememberPreference(swipeSensitivityKey, 73)

    var isShowingSwipeSensitivityDialog by remember {
        mutableStateOf(false)
    }

    if (isShowingSwipeSensitivityDialog) {
        SwipeSensitivityDialog(
            sensitivity = swipeSensitivity,
            onDismiss = { isShowingSwipeSensitivityDialog = false },
            onSave = {
                swipeSensitivity = it
                isShowingSwipeSensitivityDialog = false
            }
        )
    }

    val activityResultLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

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
        Header(title = "Player & Audio")

        SettingsEntryGroupText(title = "PLAYER")

        SwitchSettingEntry(
            title = "Persistent queue",
            text = "Save and restore playing songs",
            isChecked = persistentQueue,
            onCheckedChange = {
                persistentQueue = it
            }
        )

        if (isAtLeastAndroid6) {
            SwitchSettingEntry(
                title = "Resume playback",
                text = "When a wired or bluetooth device is connected",
                isChecked = resumePlaybackWhenDeviceConnected,
                onCheckedChange = {
                    resumePlaybackWhenDeviceConnected = it
                }
            )
        }

        SettingsGroupSpacer()

        SettingsEntryGroupText(title = "GESTURES")

        SwitchSettingEntry(
            title = "Swipe to change song",
            text = "Swipe the artwork or mini-player horizontally",
            isChecked = swipeToChangeSong,
            onCheckedChange = {
                swipeToChangeSong = it
            }
        )

        SettingsEntry(
            title = "Swipe sensitivity",
            text = "$swipeSensitivity%",
            isEnabled = swipeToChangeSong,
            onClick = {
                isShowingSwipeSensitivityDialog = true
            }
        )

        SettingsGroupSpacer()

        SettingsEntryGroupText(title = "AUDIO")

        SwitchSettingEntry(
            title = "Skip silence",
            text = "Skip silent parts during playback",
            isChecked = skipSilence,
            onCheckedChange = {
                skipSilence = it
            }
        )

        SwitchSettingEntry(
            title = "Loudness normalization",
            text = "Adjust the volume to a fixed level",
            isChecked = volumeNormalization,
            onCheckedChange = {
                volumeNormalization = it
            }
        )

        SettingsEntry(
            title = "Equalizer",
            text = "Interact with the system equalizer",
            onClick = {
                val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, binder?.player?.audioSessionId)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                    putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                }

                try {
                    activityResultLauncher.launch(intent)
                } catch (e: ActivityNotFoundException) {
                    context.toast("Couldn't find an application to equalize audio")
                }
            }
        )
    }
}

@Composable
private fun SwipeSensitivityDialog(
    sensitivity: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    val (colorPalette, typography) = LocalAppearance.current

    var value by remember(sensitivity) {
        mutableStateOf(sensitivity.toLong())
    }

    DefaultDialog(
        onDismiss = onDismiss
    ) {
        BasicText(
            text = "Swipe sensitivity",
            style = typography.s.semiBold
        )

        BasicText(
            text = "$value%",
            style = typography.xs.secondary
        )

        SeekBar(
            value = value,
            minimumValue = 0,
            maximumValue = 100,
            onDragStart = {
                value = it.coerceIn(0, 100)
            },
            onDrag = {
                value = (value + it).coerceIn(0, 100)
            },
            onDragEnd = {},
            color = colorPalette.accent,
            backgroundColor = colorPalette.background2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        )

        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            DialogTextButton(
                text = "Cancel",
                onClick = onDismiss
            )

            DialogTextButton(
                primary = true,
                text = "Done",
                onClick = {
                    onSave(value.toInt())
                }
            )
        }
    }
}
