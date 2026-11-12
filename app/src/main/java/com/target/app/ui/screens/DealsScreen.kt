package com.target.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.target.app.data.StoredDeal
import com.target.app.ui.AppViewModel
import com.target.app.ui.components.DealBadge
import com.target.app.ui.components.GradientButton
import com.target.app.ui.components.TargetRadar
import com.target.app.ui.components.bouncyClick
import com.target.app.ui.components.entrance
import com.target.app.ui.components.price
import com.target.app.ui.components.relativeTime
import com.target.app.ui.components.sharedBounds
import com.target.app.ui.theme.LocalHaptics
import com.target.app.ui.theme.Palette
import kotlinx.coroutines.launch

@Composable
fun DealsScreen(vm: AppViewModel, onOpen: (Long) -> Unit) {
    val deals by vm.deals.collectAsState()
    val settings by vm.settings.collectAsState()
    val h = LocalHaptics.current
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.statusBarsPadding().padding(top = 18.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Deals", style = MaterialTheme.typography.displaySmall, color = Palette.Text)
                    Text(
                        if (settings.dealsEnabled) "Scan auto toutes les ${settings.scanMinutes} min · sensibilité ${settings.sensitivity.label.lowercase()}"
                        else "Alertes désactivées dans les réglages",
                        style = MaterialTheme.typography.bodySmall, color = Palette.Text2,
                    )
                }
                if (deals.isNotEmpty()) Icon(Icons.Rounded.DeleteSweep, "Tout effacer", tint = Palette.Text3, modifier = Modifier.size(26.dp).bouncyClick { h.reject(); vm.clearDeals() })
            }
        }
        item {
            GradientButton(
                if (scanning) "Scan en cours…" else "Scanner maintenant", Modifier.fillMaxWidth(), height = 50.dp,
                brush = Palette.DealBrush, enabled = !scanning,
                icon = { Icon(Icons.Rounded.Radar, null, tint = Color.White) },
            ) {
                scanning = true
                scope.launch {
                    val before = vm.deals.value.size
                    vm.scanDealsNow().join()
                    scanning = false
                    if (vm.deals.value.size > before) h.deal() else h.reject()
                }
            }
        }
        if (deals.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(top = 30.dp, start = 20.dp, end = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TargetRadar(Modifier.size(140.dp), active = scanning)
                Spacer(Modifier.height(16.dp))
                Text("Aucun deal pour l'instant", style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
                Text(
                    "Target surveille tes réfs en arrière-plan et t'envoie une notif quand un article est vraiment sous sa valeur (vraie marque, bon état, prix anormalement bas).",
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Text2, textAlign = TextAlign.Center,
                )
            }
        }
        itemsIndexed(deals, key = { _, d -> d.ranked.listing.id }) { i, d ->
            SwipeDeal(d, i, Modifier.animateItem(), onRemove = { h.reject(); vm.removeDeal(d.ranked.listing.id) }) { onOpen(d.ranked.listing.id) }
        }
    }
}

@Composable
private fun SwipeDeal(d: StoredDeal, index: Int, modifier: Modifier, onRemove: () -> Unit, onClick: () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { if (it != SwipeToDismissBoxValue.Settled) { onRemove(); true } else false })
    SwipeToDismissBox(state = state, modifier = modifier, backgroundContent = {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(Palette.Accent.copy(alpha = 0.25f)).padding(20.dp), contentAlignment = Alignment.CenterEnd) {
            Text("Retirer", color = Palette.Accent, style = MaterialTheme.typography.labelLarge)
        }
    }) { DealRow(d, index, onClick) }
}

@Composable
private fun DealRow(d: StoredDeal, index: Int, onClick: () -> Unit) {
    val r = d.ranked
    val l = r.listing
    Row(
        Modifier.fillMaxWidth().entrance(l.id, index).clip(RoundedCornerShape(22.dp)).background(Palette.Surface)
            .border(1.dp, Palette.Deal.copy(alpha = 0.35f), RoundedCornerShape(22.dp)).bouncyClick(scale = 0.97f, onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(l.photoUrl, l.title, contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 92.dp, height = 116.dp).sharedBounds("img-${l.id}").clip(RoundedCornerShape(16.dp)).background(Palette.Surface2))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            DealBadge()
            Spacer(Modifier.height(6.dp))
            Text(l.brand ?: r.matchedBrand ?: "", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Palette.Text, maxLines = 1)
            Text(l.title, style = MaterialTheme.typography.bodySmall, color = Palette.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(price(l.price, l.currency), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), color = Palette.Text)
                Spacer(Modifier.width(8.dp))
                Text("~${price(Math.round(r.expectedPrice).toDouble())}", style = MaterialTheme.typography.bodySmall, color = Palette.Text3)
                Spacer(Modifier.width(8.dp))
                Text("-${r.discountPct}%", style = MaterialTheme.typography.labelLarge, color = Palette.Deal)
            }
            Text(
                listOfNotNull(l.size, l.condition.label, d.profileName, relativeTime(d.foundAt / 1000)).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium, color = Palette.Text3, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
