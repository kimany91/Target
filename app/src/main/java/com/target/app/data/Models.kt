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
)

@Serializable
data class RefImage(
    val id: String,
    val path: String,
    val addedAt: Long,
    val analysis: ImageAnalysis? = null,
    /** Tags the user removed from the automatic analysis. */
    val hidden: List<String> = emptyList(),
) {
    fun visibleItems() = analysis?.items.orEmpty().filterNot { it in hidden }
    fun visibleColors() = analysis?.colors.orEmpty().filterNot { it in hidden }
    fun visibleMaterials() = analysis?.materials.orEmpty().filterNot { it in hidden }
    fun visibleBrands() = analysis?.brands.orEmpty().filterNot { it in hidden }
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
)

@Serializable
data class StoredDeal(
    val ranked: com.target.engine.RankedListing,
    val profileId: String?,
    val profileName: String?,
    val foundAt: Long,
)
