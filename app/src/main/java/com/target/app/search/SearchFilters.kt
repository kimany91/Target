package com.target.app.search

import com.target.engine.Gender

/** Filters chosen under the search bar (price range, sizes, gender); they override the profile's own filters. */
data class SearchFilters(
    val minPrice: Int? = null,
    val maxPrice: Int? = null,
    val sizes: Set<String> = emptySet(),
    val gender: Gender? = null,
) {
    val isEmpty: Boolean get() = minPrice == null && maxPrice == null && sizes.isEmpty() && gender == null
}
