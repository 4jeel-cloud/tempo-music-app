package it.vfsfitvnm.vimusic

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import it.vfsfitvnm.innertube.InnertubeLog
import it.vfsfitvnm.innertube.NewPipeStreamResolver
import it.vfsfitvnm.vimusic.enums.CoilDiskCacheMaxSize
import it.vfsfitvnm.vimusic.utils.AppLog
import it.vfsfitvnm.vimusic.utils.coilDiskCacheMaxSizeKey
import it.vfsfitvnm.vimusic.utils.getEnum
import it.vfsfitvnm.vimusic.utils.preferences

class MainApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        InnertubeLog.error = { tag, message, throwable -> AppLog.e(tag, message, throwable) }
        InnertubeLog.info = { tag, message -> AppLog.i(tag, message) }
        installCrashHandler()
        NewPipeStreamResolver.init()
        DatabaseInitializer()
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLog.persistCrash(thread, throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(true)
            .respectCacheHeaders(false)
            .diskCache(
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil"))
                    .maxSizeBytes(
                        preferences.getEnum(
                            coilDiskCacheMaxSizeKey,
                            CoilDiskCacheMaxSize.`128MB`
                        ).bytes
                    )
                    .build()
            )
            .build()
    }
}
