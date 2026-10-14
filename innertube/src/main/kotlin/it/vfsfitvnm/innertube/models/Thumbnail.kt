package it.vfsfitvnm.innertube.models

import kotlinx.serialization.Serializable

// YouTube serves album art from (lh3|yt3).googleusercontent.com. The yt3.googleusercontent.com
// host was added later and is easy to miss, in which case the ~60px thumbnail the API returns
// is upscaled and looks blurry. Matching both hosts (and preserving the query string) fixes it.
private val GOOGLEUSERCONTENT_SIZE_PATTERN =
    Regex("^(https://(?:lh3|yt3)\\.googleusercontent\\.com/[^?]*?)=w(\\d+)-h(\\d+)[^?]*(\\?.*)?$")
private val GGPHT_SIZE_PATTERN =
    Regex("^(https://yt3\\.ggpht\\.com/[^?=]+)=(?:s\\d+|w\\d+-h\\d+)[^?]*(\\?.*)?$")

@Serializable
data class Thumbnail(
    val url: String,
    val height: Int?,
    val width: Int?
) {
    val isResizable: Boolean
        get() = !url.startsWith("https://i.ytimg.com")

    fun size(size: Int): String {
        GOOGLEUSERCONTENT_SIZE_PATTERN.matchEntire(url)?.groupValues?.let { group ->
            return "${group[1]}=w$size-h$size-p-l90-rj${group[4]}"
        }
        GGPHT_SIZE_PATTERN.matchEntire(url)?.groupValues?.let { group ->
            return "${group[1]}=w$size-h$size-p-l90-rj${group[2]}"
        }
        return url
    }
}

fun List<Thumbnail>?.largestThumbnail(): Thumbnail? {
    if (this.isNullOrEmpty()) return null
    val maxArea = maxOf { (it.width ?: 0) * (it.height ?: 0) }
    return if (maxArea > 0) first { (it.width ?: 0) * (it.height ?: 0) == maxArea } else last()
}
