package it.vfsfitvnm.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Thumbnail(
    val url: String,
    val height: Int?,
    val width: Int?
) {
    val isResizable: Boolean
        get() = !url.startsWith("https://i.ytimg.com")

    fun size(size: Int): String {
        val base = url.substringBefore("=")
        return when {
            url.startsWith("https://lh3.googleusercontent.com") -> "$base=w$size-h$size-l90-rj"
            url.startsWith("https://yt3.ggpht.com") -> "$base=w$size-h$size"
            else -> url
        }
    }
}

fun List<Thumbnail>?.largestThumbnail(): Thumbnail? {
    if (this.isNullOrEmpty()) return null
    val maxArea = maxOf { (it.width ?: 0) * (it.height ?: 0) }
    return if (maxArea > 0) first { (it.width ?: 0) * (it.height ?: 0) == maxArea } else last()
}
