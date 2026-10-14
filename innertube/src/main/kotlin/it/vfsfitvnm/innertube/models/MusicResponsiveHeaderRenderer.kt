package it.vfsfitvnm.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class MusicResponsiveHeaderRenderer(
    val title: Runs?,
    val subtitle: Runs?,
    val straplineTextOne: Runs?,
    val secondSubtitle: Runs?,
    val thumbnail: ThumbnailRenderer?,
)
