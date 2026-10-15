package it.vfsfitvnm.vimusic.widget

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Serves the (high resolution) widget artwork to the home screen process.
 *
 * The home screen renders app widgets and cannot read our internal files, and RemoteViews bitmaps
 * are capped by the ~1MB binder transaction limit, so a bitmap large enough for a 4x4 widget is not
 * possible via [android.widget.RemoteViews.setImageViewBitmap]. Instead we write the art to a PNG in
 * the cache directory and let the home screen load it from this provider, which lets it decode at
 * whatever resolution it needs without any IPC size limit.
 *
 * Only files placed inside cacheDir/widget_art are exposed, so the exported surface is limited to the
 * album art images.
 */
class WidgetArtProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val ctx = context ?: return null
        val name = uri.lastPathSegment ?: return null
        if (!name.matches(NAME_PATTERN)) return null

        val file = File(File(ctx.cacheDir, DIR), name)
        if (!file.isFile) return null

        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/png"

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0

    companion object {
        const val DIR = "widget_art"
        private val NAME_PATTERN = Regex("[A-Za-z0-9_.-]+\\.png")

        fun authority(packageName: String) = "$packageName.widgetart"

        fun uriFor(packageName: String, name: String) = Uri.parse("content://${authority(packageName)}/$name")
    }
}
