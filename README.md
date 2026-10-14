<div align="center">
    <img src="./docs/banner.png" width="640" alt="Tempo"/>
    <p>An Android application for streaming music from YouTube Music</p>
</div>

---

<p align="center">
  <img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/1.jpg" width="30%" />
  <img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/2.jpg" width="30%" />
  <img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/3.jpg" width="30%" />

  <img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/4.jpg" width="30%" />
  <img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/5.jpg" width="30%" />
  <img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/6.jpg" width="30%" />
</p>

## About

**Tempo** is a lightweight, ad-free YouTube Music client for Android.

### Highlights

- **Reliable playback.** Streaming is resolved through
  [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor), with the
  legacy InnerTube path kept as a fallback.
- **Home-screen widgets.** Now Playing, Turntable and Playlists widgets with full
  playback controls and shortcuts to your liked songs, playlists and top songs.
- **Sharp artwork.** Picks the highest-resolution thumbnail and requests it at a
  valid size, so album art stays crisp on every screen.
- **Working Quick Picks / related content.** The "Related" tab is resolved
  correctly on album, artist and playlist pages.

## Features

- Play (almost) any song or video from YouTube Music
- Background playback with a media notification
- Cache audio chunks for offline playback
- Search for songs, albums, artists, videos and playlists
- Bookmark artists and albums
- Import playlists
- Fetch, display and edit lyrics or synchronized lyrics
- Local playlist management
- Reorder songs in a playlist or the queue
- Light / Dark / Dynamic theme
- Skip silence
- Sleep timer
- Audio normalization
- Android Auto
- Persistent queue
- Home-screen media widgets
- Open YouTube / YouTube Music links (`watch`, `playlist`, `channel`)

## Installation

Grab the latest signed APK from the [Releases](https://github.com/4jeel-cloud/tempo-music-app/releases)
page and install it (you may need to allow installation from unknown sources).

> **Note:** if Android shows a "built for an older version of Android" / Play Protect
> warning, tap **Install anyway**. The app targets Android 13 (API 33).

## Building

Requirements: JDK 17 and the Android SDK (platform 33, build-tools 33.0.1).

```bash
./gradlew :app:assembleRelease
```

The signed APK is written to `app/build/outputs/apk/release/app-release.apk`.
Release signing is configured through `keystore.properties` (not committed).

## Disclaimer

This project and its contents are not affiliated with, funded, authorized, endorsed
by, or in any way associated with YouTube, Google LLC or any of its affiliates and
subsidiaries.

Any trademark, service mark, trade name, or other intellectual property rights used
in this project are owned by the respective owners.

## Acknowledgments

- [**ViMusic**](https://github.com/vfsfitvnm/ViMusic): the original project Tempo is based on.
- [**NewPipeExtractor**](https://github.com/TeamNewPipe/NewPipeExtractor): the stream extraction library that powers playback.
- [**YouTube-Internal-Clients**](https://github.com/zerodytrash/YouTube-Internal-Clients): a research project that documents hidden YouTube API clients.
- [**ionicons**](https://github.com/ionic-team/ionicons): premium hand-crafted icons built by Ionic.

## License

Licensed under the [GNU General Public License v3.0](./LICENSE).
