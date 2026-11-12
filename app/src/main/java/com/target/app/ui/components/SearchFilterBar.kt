package com.target.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.target.app.search.SearchFilters
import com.target.app.ui.theme.LocalHaptics
import com.target.app.ui.theme.Palette
import com.target.engine.Gender

private const val PRICE_MAX = 300f
private val LETTER_SIZES = listOf("XXS", "XS", "S", "M", "L", "XL", "XXL")
private val NUMBER_SIZES = listOf("34", "36", "38", "40", "42", "44", "46")
private val WAIST_SIZES = listOf("W28", "W29", "W30", "W31", "W32", "W33", "W34", "W36")

private enum class Sheet { PRICE, SIZE, GENDER }

/** Price range, sizes and gender, right under the search bar. Changing them re-runs the current search. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchFilterBar(f: SearchFilters, onChange: (SearchFilters) -> Unit, modifier: Modifier = Modifier) {
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val h = LocalHaptics.current
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val price = when {
            f.minPrice == null && f.maxPrice == null -> "Prix"
            f.maxPrice == null -> "Dès ${f.minPrice} €"
            f.minPrice == null -> "Jusqu'à ${f.maxPrice} €"
            else -> "${f.minPrice}–${f.maxPrice} €"
        }
        FilterPill("€ $price", f.minPrice != null || f.maxPrice != null) { sheet = Sheet.PRICE }
        FilterPill("📏 " + if (f.sizes.isEmpty()) "Taille" else f.sizes.joinToString(", "), f.sizes.isNotEmpty()) { sheet = Sheet.SIZE }
        FilterPill("👤 " + (f.gender?.label ?: "Homme / Femme"), f.gender != null) { sheet = Sheet.GENDER }
        if (!f.isEmpty) FilterPill("✕ Effacer", false) { h.reject(); onChange(SearchFilters()) }
    }

    val current = sheet ?: return
    var draft by remember(current) { mutableStateOf(f) }
    var range by remember(current) { mutableStateOf((f.minPrice ?: 0).toFloat()..(f.maxPrice?.toFloat() ?: PRICE_MAX)) }
    ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Palette.Surface) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            when (current) {
                Sheet.PRICE -> {
                    Text("Prix", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
                    Spacer(Modifier.height(4.dp))
                    val lo = range.start.toInt(); val hi = range.endInclusive.toInt()
                    Text(
                        "${if (lo <= 0) "0" else lo} € — ${if (hi >= PRICE_MAX.toInt()) "${PRICE_MAX.toInt()} € et +" else "$hi €"}",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = Palette.Accent,
                    )
                    RangeSlider(
                        value = range,
                        onValueChange = { r -> if (r.start.toInt() / 5 != range.start.toInt() / 5 || r.endInclusive.toInt() / 5 != range.endInclusive.toInt() / 5) h.segment(); range = r },
                        valueRange = 0f..PRICE_MAX,
                        colors = SliderDefaults.colors(thumbColor = Palette.Accent, activeTrackColor = Palette.Accent, inactiveTrackColor = Palette.Surface3),
                    )
                }
                Sheet.SIZE -> {
                    Text("Taille", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
                    Text("Seules les annonces dans ces tailles s'affichent", style = MaterialTheme.typography.bodySmall, color = Palette.Text2)
                    for (group in listOf(LETTER_SIZES, NUMBER_SIZES, WAIST_SIZES)) {
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            group.forEach { s ->
                                val on = s in draft.sizes
                                Choice(s, on) { h.toggle(!on); draft = draft.copy(sizes = if (on) draft.sizes - s else draft.sizes + s) }
                            }
                        }
                    }
                }
                Sheet.GENDER -> {
                    Text("Homme / Femme", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
                    Text("Les pièces unisexes restent affichées", style = MaterialTheme.typography.bodySmall, color = Palette.Text2)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Choice("Tous", draft.gender == null) { h.segment(); draft = draft.copy(gender = null) }
                        Choice("Homme", draft.gender == Gender.MEN) { h.segment(); draft = draft.copy(gender = Gender.MEN) }
                        Choice("Femme", draft.gender == Gender.WOMEN) { h.segment(); draft = draft.copy(gender = Gender.WOMEN) }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            GradientButton("Appliquer", Modifier.fillMaxWidth(), height = 50.dp) {
                val result = if (current == Sheet.PRICE) {
                    val lo = range.start.toInt(); val hi = range.endInclusive.toInt()
                    draft.copy(minPrice = lo.takeIf { it > 0 }, maxPrice = hi.takeIf { it < PRICE_MAX.toInt() })
                } else draft
                sheet = null
                onChange(result)
            }
        }
    }
}

@Composable
private fun FilterPill(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (active) Palette.Accent.copy(alpha = 0.18f) else Palette.Surface)
            .border(1.dp, if (active) Palette.Accent else Palette.Outline, RoundedCornerShape(50))
            .bouncyClick(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = if (active) Palette.Text else Palette.Text2) }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(12.dp)).background(if (selected) Palette.Accent.copy(alpha = 0.2f) else Palette.Surface2)
            .border(1.dp, if (selected) Palette.Accent else Palette.Outline, RoundedCornerShape(12.dp))
            .bouncyClick(haptic = false, onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp)
    ) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Palette.Text else Palette.Text2) }
}
