package com.target.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.target.app.ui.AppViewModel
import com.target.app.ui.components.DealBadge
import com.target.app.ui.components.GradientButton
import com.target.app.ui.components.Pill
import com.target.app.ui.components.bouncyClick
import com.target.app.ui.components.price
import com.target.app.ui.components.relativeTime
import com.target.app.ui.components.sharedBounds
import com.target.app.ui.components.shimmer
import com.target.app.ui.theme.LocalHaptics
import com.target.app.ui.theme.Palette
import com.target.engine.Lexicon
import com.target.engine.RankedListing

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(vm: AppViewModel, id: Long, onBack: () -> Unit) {
    val r = remember(id) { vm.ranked(id) }
    val details by vm.details.collectAsState()
    val h = LocalHaptics.current
    val ctx = LocalContext.current
    LaunchedEffect(id) { vm.loadDetails(id); if (r?.deal?.isDeal == true) h.deal() }
    if (r == null) { Box(Modifier.fillMaxSize().background(Palette.Bg)) { BackButton(onBack) }; return }
    val l = details[id] ?: r.listing
    val photos = (listOfNotNull(r.listing.photoUrl) + l.photos).distinct().ifEmpty { listOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(Palette.Bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
            val pager = rememberPagerState { photos.size }
            Box(Modifier.fillMaxWidth().aspectRatio(0.8f)) {
                HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                    AsyncImage(
                        ImageRequest.Builder(ctx).data(photos[page]).crossfade(200).build(), l.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().then(if (page == 0) Modifier.sharedBounds("img-${l.id}") else Modifier).background(Palette.Surface2),
                    )
                }
                Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))))
                Box(Modifier.fillMaxWidth().height(90.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Palette.Bg))))
                if (photos.size > 1) Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(photos.size) { i ->
                        val on = i == pager.currentPage
                        Box(Modifier.height(6.dp).width(if (on) 18.dp else 6.dp).clip(RoundedCornerShape(50)).background(if (on) Color.White else Color.White.copy(alpha = 0.4f)).animateContentSize())
                    }
                }
                LaunchedEffect(pager.currentPage) { if (pager.currentPage > 0) h.segment() }
            }

            val appear = remember { Animatable(0f) }
            LaunchedEffect(Unit) { appear.animateTo(1f, tween(450, 120, FastOutSlowInEasing)) }
            Column(
                Modifier.padding(horizontal = 18.dp).graphicsLayer { alpha = appear.value; translationY = (1 - appear.value) * 40f },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (r.deal?.isDeal == true) DealBadge()
                    if (r.discountPct >= 15) Pill("-${r.discountPct}% vs marché", Palette.Accent)
                    if (r.suspicious || r.deal?.suspectedFake == true) Pill("⚠ à vérifier", Palette.Warn, Color.Black)
                }
                Spacer(Modifier.height(8.dp))
                Text(l.brand ?: r.matchedBrand ?: "Sans marque", style = MaterialTheme.typography.titleMedium, color = Palette.Accent2)
                Text(l.title, style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(price(l.price, l.currency), style = MaterialTheme.typography.displaySmall.copy(fontSize = 38.sp), color = Palette.Text)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("${price(l.buyerPrice, l.currency)} avec protection", style = MaterialTheme.typography.bodySmall, color = Palette.Text2)
                        if (r.expectedPrice > l.buyerPrice) Text("valeur ~${price(Math.round(r.expectedPrice).toDouble())}", style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough), color = Palette.Text3)
                    }
                }
                Spacer(Modifier.height(16.dp))
                ValueMeter(r)
                Spacer(Modifier.height(16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    l.size?.let { InfoChip("Taille", it) }
                    InfoChip("État", l.condition.label)
                    r.matchedItem?.let { Lexicon.concept(it)?.label }?.let { InfoChip("Pièce", it) }
                    InfoChip("Pays", l.host.substringAfterLast('.').uppercase().replace("UK", "UK"))
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    relativeTime(l.createdAt)?.let { Meta(Icons.Rounded.Schedule, it) }
                    Meta(Icons.Rounded.Favorite, "${l.favourites}")
                    if (l.views > 0) Meta(Icons.Rounded.Visibility, "${l.views}")
                    l.seller?.let { Meta(Icons.Rounded.Person, it) }
                }
                Spacer(Modifier.height(18.dp))
                Text("Description", style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                Spacer(Modifier.height(6.dp))
                if (l.description != null) Text(l.description!!, style = MaterialTheme.typography.bodyMedium, color = Palette.Text2)
                else if (details.containsKey(id)) Text("Pas de description.", style = MaterialTheme.typography.bodyMedium, color = Palette.Text3)
                else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { repeat(3) { Box(Modifier.fillMaxWidth(if (it == 2) 0.6f else 1f).height(12.dp).clip(RoundedCornerShape(6.dp)).shimmer()) } }
                Spacer(Modifier.height(18.dp))
                ScoreCard(r)
            }
        }
        BackButton(onBack)
        Box(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp).size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
                .bouncyClick { share(ctx, l.title, l.url) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Share, "Partager", tint = Color.White) }
        AnimatedVisibility(true, Modifier.align(Alignment.BottomCenter), enter = slideInVertically { it } + fadeIn()) {
            Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Palette.Bg, Palette.Bg))).navigationBarsPadding().padding(16.dp)) {
                GradientButton("Aller sur Vinted", Modifier.fillMaxWidth(), icon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = Color.White) }) {
                    openInVinted(ctx, l.url)
                }
            }
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    Box(
        Modifier.statusBarsPadding().padding(12.dp).size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).bouncyClick(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", tint = Color.White) }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(Palette.Surface).border(1.dp, Palette.Outline, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Text3)
        Text(value, style = MaterialTheme.typography.labelLarge, color = Palette.Text)
    }
}

@Composable
private fun Meta(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Palette.Text3, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Palette.Text2)
    }
}

/** Gauge comparing the price to the estimated market value. */
@Composable
private fun ValueMeter(r: RankedListing) {
    val ratio = (r.listing.buyerPrice / r.expectedPrice).coerceIn(0.0, 2.0)
    val pos = remember { Animatable(0f) }
    LaunchedEffect(ratio) { pos.animateTo((ratio / 2.0).toFloat(), tween(900, 200, FastOutSlowInEasing)) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.Surface).padding(14.dp)) {
        Row {
            Text("Prix vs valeur estimée", style = MaterialTheme.typography.titleSmall, color = Palette.Text, modifier = Modifier.weight(1f))
            Text(
                when { ratio < 0.5 -> "Exceptionnel"; ratio < 0.8 -> "Très bon prix"; ratio < 1.05 -> "Prix du marché"; else -> "Au-dessus" },
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = when { ratio < 0.5 -> Palette.Deal; ratio < 0.8 -> Palette.ColorTag; ratio < 1.05 -> Palette.Warn; else -> Palette.Accent },
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(10.dp)) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Palette.Deal, Palette.ColorTag, Palette.Warn, Palette.Accent))))
            Box(Modifier.fillMaxHeight().fillMaxWidth(pos.value.coerceIn(0.02f, 1f))) {
                Box(Modifier.align(Alignment.CenterEnd).size(18.dp).clip(CircleShape).background(Color.White).border(3.dp, Palette.Bg, CircleShape))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Estimation : ${price(Math.round(r.expectedPrice).toDouble())} d'après les annonces comparables et la cote de la marque" +
                (r.deal?.let { " · ${it.reason}" } ?: ""),
            style = MaterialTheme.typography.bodySmall, color = Palette.Text3,
        )
    }
}

@Composable
private fun ScoreCard(r: RankedListing) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.Surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Pourquoi ce score", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
        Bar("Correspondance à tes réfs", r.relevance, Palette.Item)
        Bar("Rapport qualité-prix", (r.valueRatio / 2.0).coerceIn(0.0, 1.0), Palette.Deal)
        Bar("Fraîcheur", r.recency, Palette.Accent2)
        r.visual?.let { Bar("Ressemblance visuelle", ((it - 0.3) / 0.6).coerceIn(0.0, 1.0), Palette.Info) }
        if (r.hits > 1) Text("Trouvée par ${r.hits} recherches différentes", style = MaterialTheme.typography.bodySmall, color = Palette.Text3)
    }
}

@Composable
private fun Bar(label: String, value: Double, color: Color) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(value) { a.animateTo(value.toFloat().coerceIn(0f, 1f), tween(800, 250, FastOutSlowInEasing)) }
    Column {
        Row { Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.Text2, modifier = Modifier.weight(1f)); Text("${(value * 100).toInt()}", style = MaterialTheme.typography.labelMedium, color = Palette.Text) }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Palette.Surface3)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(a.value).clip(RoundedCornerShape(50)).background(color))
        }
    }
}

/** Opens the listing in the Vinted app when installed, otherwise in the browser. */
fun openInVinted(ctx: Context, url: String) {
    val uri = Uri.parse(url)
    val app = Intent(Intent.ACTION_VIEW, uri).setPackage("fr.vinted").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { ctx.startActivity(app) } catch (_: ActivityNotFoundException) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

private fun share(ctx: Context, title: String, url: String) {
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "$title\n$url")
    ctx.startActivity(Intent.createChooser(i, "Partager").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
