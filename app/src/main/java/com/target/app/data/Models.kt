package com.target.app.data

import kotlinx.serialization.Serializable

/** Result of the on-device "Lens" analysis of a reference photo. */
@Serializable
data class ImageAnalysis(
    val labels: List<String> = emptyList(),
    val items: List<String> = emptyList(),
    val materials: List<String> = emptyList(),
    val colors: List<String> = emptyList(),
    val brands: List<String> = emptyList(),
    val ocrText: String = "",
    val embedding: List<Float> = emptyList(),
    /** Vision model + analysis version that produced this; refs are re-analysed when it changes. */
    val model: String = "",
    /** Confidence (0–1) of each detected piece / colour / material / brand, for display. */
    val scores: Map<String, Float> = emptyMap(),
    /** Tags proposed but off by default (an uncertain logo): the user turns them on with a tap. */
    val inactive: List<String> = emptyList(),
    /** Styles the photo looks like (Taste style ids, best first). */
    val styles: List<String> = emptyList(),
    /** Beauty of the piece in the photo /10, from the head learnt on the user's examples. */
    val beauty: Double? = null,
) {
    fun activeBrands() = brands.filterNot { it in inactive }
}

@Serializable
data class RefImage(
    val id: String,
    val path: String,
    val addedAt: Long,
    val analysis: ImageAnalysis? = null,
    /** Tags the user removed from the automatic analysis. */
    val hidden: List<String> = emptyList(),
) {
    /** A tag is on by default unless proposed as inactive; a tap ([hidden]) flips it. */
    fun isOn(tag: String) = (tag in analysis?.inactive.orEmpty()) == (tag in hidden)
    fun visibleItems() = analysis?.items.orEmpty().filter { isOn(it) }
    fun visibleColors() = analysis?.colors.orEmpty().filter { isOn(it) }
    fun visibleMaterials() = analysis?.materials.orEmpty().filter { isOn(it) }
    fun visibleBrands() = analysis?.brands.orEmpty().filter { isOn(it) }
}

@Serializable
data class Profile(
    val id: String,
    val name: String,
    val emoji: String = "🎯",
    val hue: Float = 350f,
    val keywords: List<String> = emptyList(),
    val images: List<RefImage> = emptyList(),
    val maxPrice: Double? = null,
    val sizes: List<String> = emptyList(),
    val dealsEnabled: Boolean = true,
    /** Photo embeddings of listings the user liked / disliked (personal taste, capped). */
    val liked: List<List<Float>> = emptyList(),
    val disliked: List<List<Float>> = emptyList(),
)

@Serializable
data class StoredDeal(
    val ranked: com.target.engine.RankedListing,
    val profileId: String?,
    val profileName: String?,
    val foundAt: Long,
)
