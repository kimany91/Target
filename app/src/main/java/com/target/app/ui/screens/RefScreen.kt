package com.target.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.target.app.data.Profile
import com.target.app.data.RefImage
import com.target.app.ui.AppViewModel
import com.target.app.ui.components.GradientButton
import com.target.app.ui.components.SectionCard
import com.target.app.ui.components.TagChip
import com.target.app.ui.components.bouncyClick
import com.target.app.ui.components.entrance
import com.target.app.ui.components.kindColor
import com.target.app.ui.components.kindLabel
import com.target.app.ui.theme.LocalHaptics
import com.target.app.ui.theme.Palette
import com.target.engine.Brands
import com.target.engine.KeywordKind
import com.target.engine.Lexicon
import com.target.engine.QueryParser
import com.target.engine.Styles
import com.target.engine.Text as T
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RefScreen(vm: AppViewModel) {
    val profiles by vm.profiles.collectAsState()
    val profile by vm.active.collectAsState()
    val analyzing by vm.analyzing.collectAsState()
    val h = LocalHaptics.current
    var editing by remember { mutableStateOf<Profile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var openImage by remember { mutableStateOf<String?>(null) }
    var pinterest by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(8)) { uris ->
        if (uris.isNotEmpty()) { h.confirm(); vm.addImages(uris) }
    }
    val p = profile ?: return

    LazyColumn(
        Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = 18.dp, start = 4.dp)) {
                Text("Réfs", style = MaterialTheme.typography.displaySmall, color = Palette.Text)
                Text("Ce que Target traque pour toi", style = MaterialTheme.typography.bodyMedium, color = Palette.Text2)
            }
        }
        item {
            ProfileRow(profiles, p.id, onSelect = { h.tick(); vm.selectProfile(it) }, onEdit = { h.longPress(); editing = it }, onCreate = { creating = true })
        }
        item {
            SectionCard(
                "Photos de réf",
                subtitle = "L'IA reconnaît la pièce, les couleurs, la marque et le style visuel",
                trailing = { CircleIcon(Icons.Rounded.AddPhotoAlternate) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } },
            ) {
                if (p.images.isEmpty()) {
                    EmptyPhotos { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(p.images, key = { it.id }) { img ->
                            RefThumb(img, img.id in analyzing, Modifier.animateItem()) { h.click(); openImage = img.id }
                        }
                        item {
                            Box(
                                Modifier.size(width = 120.dp, height = 160.dp).clip(RoundedCornerShape(18.dp)).background(Palette.Surface2)
                                    .border(1.dp, Palette.Outline, RoundedCornerShape(18.dp))
                                    .bouncyClick { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Add, null, tint = Palette.Text2, modifier = Modifier.size(32.dp)) }
                        }
                    }
                }
            }
        }
        item {
            // Pinterest inside the app: search, tap the pictures you like, add them as refs
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFFE60023).copy(alpha = 0.22f), Palette.Surface)))
                    .border(1.dp, Color(0xFFE60023).copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                    .bouncyClick { h.click(); pinterest = true }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFFE60023)), contentAlignment = Alignment.Center) {
                    Text("P", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black), color = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Trouver des réfs sur Pinterest", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
                    Text("Cherche dans l'appli, touche les images, ajoute-les — sans compte", style = MaterialTheme.typography.bodySmall, color = Palette.Text2)
                }
                Icon(Icons.Rounded.Add, null, tint = Palette.Text2)
            }
        }
        item { KeywordsCard(p, vm) }
        item { FiltersCard(p, vm) }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Palette.Surface).border(1.dp, Palette.Outline.copy(alpha = 0.6f), RoundedCornerShape(20.dp)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.NotificationsActive, null, tint = Palette.Deal)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Alertes Deal pour ce profil", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
                    Text("Notification si une offre exceptionnelle correspond", style = MaterialTheme.typography.bodySmall, color = Palette.Text2)
                }
                Switch(p.dealsEnabled, { h.toggle(it); vm.setProfileDeals(it) }, colors = SwitchDefaults.colors(checkedTrackColor = Palette.Deal, checkedThumbColor = Color.Black))
            }
        }
        item {
            val ready = p.keywords.isNotEmpty() || p.images.isNotEmpty()
            GradientButton(
                if (ready) "Lancer la recherche" else "Ajoute une réf pour commencer",
                Modifier.fillMaxWidth(), enabled = ready,
                icon = { Icon(Icons.Rounded.TrackChanges, null, tint = Color.White) },
            ) { vm.searchProfile() }
        }
    }

    if (creating) ProfileDialog(null, onDismiss = { creating = false }) { name, emoji, hue -> h.success(); vm.createProfile(name, emoji, hue); creating = false }
    editing?.let { e ->
        ProfileDialog(e, onDismiss = { editing = null }, onDelete = if (profiles.size > 1) ({ h.reject(); vm.deleteProfile(e.id); editing = null }) else null) { name, emoji, hue ->
            vm.editProfile(e.id, name, emoji, hue); editing = null
        }
    }
    if (pinterest) PinterestBrowser(
        vm = vm,
        initialQuery = p.keywords.joinToString(" ").let { if (it.isBlank()) "outfit" else "$it outfit" },
        onAdd = { urls -> vm.addImageUrls(urls) },
        onDismiss = { pinterest = false },
    )
    openImage?.let { id ->
        val img = p.images.firstOrNull { it.id == id }
        if (img == null) openImage = null else ImageSheet(img, id in analyzing, vm, onDismiss = { openImage = null })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileRow(profiles: List<Profile>, activeId: String, onSelect: (String) -> Unit, onEdit: (Profile) -> Unit, onCreate: () -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
        items(profiles, key = { it.id }) { pr ->
            val active = pr.id == activeId
            val c = Palette.hueColor(pr.hue)
            Row(
                Modifier.animateItem()
                    .clip(RoundedCornerShape(50))
                    .background(if (active) c.copy(alpha = 0.18f) else Palette.Surface)
                    .border(if (active) 1.5.dp else 1.dp, if (active) c else Palette.Outline, RoundedCornerShape(50))
                    .combinedClickable(onClick = { onSelect(pr.id) }, onLongClick = { onEdit(pr) })
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(pr.emoji)
                Spacer(Modifier.width(6.dp))
                Text(pr.name, style = MaterialTheme.typography.labelLarge, color = if (active) Palette.Text else Palette.Text2)
                if (active) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Edit, "Modifier", tint = Palette.Text3, modifier = Modifier.size(14.dp).bouncyClick { onEdit(pr) })
                }
            }
        }
        item {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Palette.Surface).border(1.dp, Palette.Outline, RoundedCornerShape(50))
                    .bouncyClick(onClick = onCreate).padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, null, tint = Palette.Text2, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Profil", style = MaterialTheme.typography.labelLarge, color = Palette.Text2)
            }
        }
    }
}

@Composable
private fun CircleIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(Palette.Surface3).bouncyClick(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Palette.Text)
    }
}

@Composable
private fun EmptyPhotos(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .border(1.5.dp, Brush.linearGradient(listOf(Palette.Accent.copy(alpha = 0.6f), Palette.Accent2.copy(alpha = 0.4f))), RoundedCornerShape(18.dp))
            .bouncyClick(onClick = onAdd).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.AddPhotoAlternate, null, tint = Palette.Accent, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(8.dp))
        Text("Ajoute des photos de vêtements", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
        Text("Captures Insta, Pinterest, photos… Target retrouve le même ou le même style sur Vinted", style = MaterialTheme.typography.bodySmall, color = Palette.Text2)
    }
}

/** Reference photo with a Lens-like scanning line while the on-device analysis runs. */
@Composable
private fun RefThumb(img: RefImage, analyzing: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.size(width = 120.dp, height = 160.dp).entrance(img.id, 0).clip(RoundedCornerShape(18.dp)).bouncyClick(onClick = onClick)) {
        AsyncImage(File(img.path), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().background(Palette.Surface2))
        if (analyzing) {
            val t = rememberInfiniteTransition(label = "scan")
            val y by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "y")
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
            Box(
                Modifier.fillMaxWidth().height(36.dp)
                    .padding(top = (y * 124).dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Palette.Accent.copy(alpha = 0.55f), Color.Transparent)))
            )
            Text("Analyse…", style = MaterialTheme.typography.labelMedium, color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
        } else {
            val tags = (img.visibleBrands().mapNotNull { Brands.byId(it)?.name } + img.visibleItems().mapNotNull { Lexicon.concept(it)?.label })
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))))
            Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                tags.take(2).forEach { Text(it, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1) }
                Row {
                    img.visibleColors().take(3).forEach { cid ->
                        Lexicon.concept(cid)?.rgb?.let { rgb ->
                            Box(Modifier.padding(end = 4.dp, top = 3.dp).size(10.dp).clip(CircleShape).background(Color(0xFF000000 or rgb.toLong())).border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ImageSheet(img: RefImage, analyzing: Boolean, vm: AppViewModel, onDismiss: () -> Unit) {
    val h = LocalHaptics.current
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Palette.Surface) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.Top) {
                AsyncImage(File(img.path), null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = 110.dp, height = 146.dp).clip(RoundedCornerShape(16.dp)))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Analyse IA", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
                    Text(
                        if (analyzing) "Analyse en cours…" else "Touche un tag pour l'activer ou l'ignorer",
                        style = MaterialTheme.typography.bodySmall, color = Palette.Text2,
                    )
                    val an = img.analysis
                    if (!analyzing && an != null) {
                        an.beauty?.let { b ->
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("%.1f".format(b), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (b >= 7) Palette.Deal else if (b >= 5) Palette.Text else Palette.Accent)
                                Text(" /10 beauté", style = MaterialTheme.typography.bodySmall, color = Palette.Text2)
                            }
                        }
                        if (an.styles.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text("Style " + an.styles.mapNotNull { com.target.engine.Styles.byId(it)?.label }.joinToString(" · "),
                                style = MaterialTheme.typography.labelLarge, color = Palette.Style)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            val a = img.analysis
            fun pct(id: String) = a?.scores?.get(id)?.let { " · ${(it * 100).toInt()} %" } ?: ""
            @Composable
            fun Section(title: String, tags: List<Triple<String, String, KeywordKind>>, caption: (String) -> String? = { null }) {
                if (tags.isEmpty()) return
                Text(title, style = MaterialTheme.typography.labelLarge, color = Palette.Text3, modifier = Modifier.padding(bottom = 6.dp, top = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    tags.forEach { (id, label, kind) ->
                        TagChip(label, kindColor(kind), caption = caption(id), selected = img.isOn(id),
                            onClick = { h.toggle(!img.isOn(id)); vm.toggleImageTag(img.id, id) })
                    }
                }
            }
            if (a != null && !analyzing) {
                Section("Pièces", a.items.map { Triple(it, Lexicon.concept(it)?.label ?: it, KeywordKind.ITEM) }) { "Pièce" + pct(it) }
                Section("Couleurs", a.colors.map { Triple(it, Lexicon.concept(it)?.label ?: it, KeywordKind.COLOR) })
                Section("Matière / motif", a.materials.map { Triple(it, Lexicon.concept(it)?.label ?: it.replaceFirstChar { c -> c.uppercase() }, KeywordKind.MATERIAL) })
                Section("Marque", a.brands.map { Triple(it, Brands.byId(it)?.name ?: it, KeywordKind.BRAND) }) {
                    when {
                        it in a.inactive -> "Logo probable${pct(it)} · touche pour l'utiliser"
                        (a.scores[it] ?: 0f) >= 1f -> "Écrit sur la photo"
                        else -> "Logo reconnu${pct(it)}"
                    }
                }
            }
            if (a != null && !analyzing && a.items.isEmpty() && a.colors.isEmpty() && a.brands.isEmpty())
                Text("Rien d'identifiable : ajoute des mots-clés pour guider la recherche. La ressemblance visuelle sera quand même utilisée.", style = MaterialTheme.typography.bodySmall, color = Palette.Text2)
            if ((a?.embedding?.size ?: 0) > 0) {
                Spacer(Modifier.height(12.dp))
                Text("◉ Empreinte visuelle prête : les résultats qui ressemblent à cette photo remontent en premier.", style = MaterialTheme.typography.bodySmall, color = Palette.Info)
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton({ vm.reanalyze(img.id) }) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Réanalyser") }
                Spacer(Modifier.weight(1f))
                TextButton({ h.reject(); vm.removeImage(img.id); onDismiss() }) {
                    Icon(Icons.Rounded.Delete, null, tint = Palette.Accent); Spacer(Modifier.width(6.dp)); Text("Supprimer", color = Palette.Accent)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeywordsCard(p: Profile, vm: AppViewModel) {
    val h = LocalHaptics.current
    var input by remember(p.id) { mutableStateOf("") }
    val parsed = remember(input) { if (input.isBlank()) null else QueryParser.parse(input) }
    SectionCard("Mots-clés", subtitle = "Styles, marques, pièces, matières… dans n'importe quelle langue") {
        OutlinedTextField(
            value = input, onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Old money, Ralph Lauren, knit…", color = Palette.Text3) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (input.isNotBlank()) { h.confirm(); vm.addKeyword(input); input = "" } }),
            trailingIcon = {
                if (input.isNotBlank()) Box(Modifier.size(36.dp).clip(CircleShape).background(Palette.AccentBrush).bouncyClick { h.confirm(); vm.addKeyword(input); input = "" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, null, tint = Color.White)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Palette.Accent, unfocusedBorderColor = Palette.Outline,
                focusedContainerColor = Palette.Surface2, unfocusedContainerColor = Palette.Surface2, cursorColor = Palette.Accent,
            ),
        )
        // live recognition preview: "ralph laurent" → Ralph Lauren · Marque (corrigé)
        AnimatedVisibility(parsed != null && parsed.recognized.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                parsed?.recognized?.take(4)?.forEach { r ->
                    Text(
                        (if (r.corrected) "✓ " else "") + r.label + " · " + kindLabel(r.kind),
                        style = MaterialTheme.typography.labelMedium, color = kindColor(r.kind),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val suggestions = remember(input, p.keywords) { suggestions(input, p.keywords) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(suggestions, key = { it.first + it.second }) { (label, kind) ->
                TagChip(label, kindColor(kind), selected = false, modifier = Modifier.animateItem(), onClick = { h.confirm(); vm.addKeyword(if (kind == KeywordKind.ITEM || kind == KeywordKind.MATERIAL) label.substringBefore(" / ") else label); input = "" })
            }
        }
        Spacer(Modifier.height(12.dp))
        if (p.keywords.isEmpty()) {
            Text("Aucun mot-clé pour l'instant.", style = MaterialTheme.typography.bodySmall, color = Palette.Text3)
        }
        FlowRow(Modifier.animateContentSize(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            p.keywords.forEach { k ->
                val r = QueryParser.parse(k)
                val first = r.recognized.firstOrNull()
                val kind = first?.kind ?: KeywordKind.FREE
                // caption = what Target understood: "Pièce · pull / maille", "Marque · Ralph Lauren (corrigé)"
                val meaning = r.recognized.joinToString(" + ") { it.label }.takeIf { it.isNotEmpty() && !it.equals(k, true) }
                TagChip(k, kindColor(kind), caption = kindLabel(kind) + (meaning?.let { " · $it" } ?: ""), onRemove = { vm.removeKeyword(k) })
            }
        }
    }
}

private fun suggestions(input: String, existing: List<String>): List<Pair<String, KeywordKind>> {
    val have = existing.map { T.normalize(it) }.toSet()
    val q = T.normalize(input)
    val all = listOf("Homme" to KeywordKind.GENDER, "Femme" to KeywordKind.GENDER) + Styles.all.map { it.label to KeywordKind.STYLE } +
        Brands.all.sortedByDescending { it.base.coerceAtMost(60.0) }.map { it.name to KeywordKind.BRAND } +
        Lexicon.items.map { it.label to KeywordKind.ITEM } +
        Lexicon.materials.map { it.label to KeywordKind.MATERIAL }
    val list = if (q.isEmpty()) {
        listOf("Homme" to KeywordKind.GENDER, "Femme" to KeywordKind.GENDER) +
            Styles.all.take(8).map { it.label to KeywordKind.STYLE } +
            listOf("Ralph Lauren", "Carhartt", "Stüssy", "Lacoste", "The North Face", "Arc'teryx", "Levi's", "Nike").map { it to KeywordKind.BRAND } +
            listOf("knit", "puffer", "jeans", "leatherjacket", "sneakers").mapNotNull { Lexicon.concept(it)?.label?.let { l -> l to KeywordKind.ITEM } }
    } else all.filter { T.normalize(it.first).contains(q) }
    return list.filter { T.normalize(it.first) !in have }.distinctBy { it.first }.take(14)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FiltersCard(p: Profile, vm: AppViewModel) {
    val h = LocalHaptics.current
    SectionCard("Filtres", subtitle = "Optionnels, appliqués aux résultats et aux deals") {
        var slider by remember(p.id) { mutableFloatStateOf((p.maxPrice ?: 0.0).toFloat()) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Prix max", style = MaterialTheme.typography.titleSmall, color = Palette.Text, modifier = Modifier.weight(1f))
            Text(if (slider < 1f) "Sans limite" else "${slider.toInt()} €", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Palette.Accent)
        }
        Slider(
            value = slider, onValueChange = { v -> if (v.toInt() / 5 != slider.toInt() / 5) h.segment(); slider = v },
            onValueChangeFinished = { vm.setMaxPrice(if (slider < 1f) null else slider.toDouble()) },
            valueRange = 0f..300f,
            colors = SliderDefaults.colors(thumbColor = Palette.Accent, activeTrackColor = Palette.Accent, inactiveTrackColor = Palette.Surface3),
        )
        Spacer(Modifier.height(6.dp))
        Text("Tailles", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
        Spacer(Modifier.height(8.dp))
        val sizes = listOf("XS", "S", "M", "L", "XL", "XXL") + p.sizes.filter { it !in listOf("XS", "S", "M", "L", "XL", "XXL") }
        var custom by remember { mutableStateOf("") }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sizes.forEach { s ->
                val on = s in p.sizes
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) Palette.Accent.copy(alpha = 0.2f) else Palette.Surface2)
                        .border(1.dp, if (on) Palette.Accent else Palette.Outline, RoundedCornerShape(12.dp))
                        .bouncyClick(haptic = false) { h.toggle(!on); vm.toggleSize(s) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) { Text(s, style = MaterialTheme.typography.labelLarge, color = if (on) Palette.Text else Palette.Text2) }
            }
            OutlinedTextField(
                custom, { custom = it.take(6) }, Modifier.width(110.dp), singleLine = true,
                placeholder = { Text("42, W32…", color = Palette.Text3) }, shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (custom.isNotBlank()) { vm.toggleSize(custom.trim().uppercase()); custom = "" } }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Accent, unfocusedBorderColor = Palette.Outline),
            )
        }
    }
}

private val EMOJIS = listOf("🎯", "🧥", "👟", "👜", "💎", "🔥", "🌿", "🖤", "🧢", "👗", "⭐", "🦋")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileDialog(existing: Profile?, onDismiss: () -> Unit, onDelete: (() -> Unit)? = null, onSave: (String, String, Float) -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var emoji by remember { mutableStateOf(existing?.emoji ?: "🎯") }
    var hue by remember { mutableFloatStateOf(existing?.hue ?: listOf(350f, 200f, 140f, 280f, 30f).random()) }
    val h = LocalHaptics.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(if (existing == null) "Nouveau profil" else "Modifier le profil", color = Palette.Text) },
        text = {
            Column {
                OutlinedTextField(name, { name = it.take(24) }, singleLine = true, placeholder = { Text("Ma sœur, Hiver, Sneakers…") },
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Accent, unfocusedBorderColor = Palette.Outline))
                Spacer(Modifier.height(14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EMOJIS.forEach { e ->
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(if (e == emoji) Palette.hueColor(hue).copy(alpha = 0.25f) else Palette.Surface2)
                                .border(1.dp, if (e == emoji) Palette.hueColor(hue) else Color.Transparent, CircleShape)
                                .bouncyClick { emoji = e },
                            contentAlignment = Alignment.Center,
                        ) { Text(e) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient((0..6).map { Palette.hueColor(it * 60f) })))
                Slider(hue, { if (it.toInt() / 20 != hue.toInt() / 20) h.segment(); hue = it }, valueRange = 0f..360f,
                    colors = SliderDefaults.colors(thumbColor = Palette.hueColor(hue), activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent))
            }
        },
        confirmButton = { TextButton({ onSave(name, emoji, hue) }) { Text("Enregistrer", color = Palette.Accent) } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onDelete) { Text("Supprimer", color = Palette.Text3) }
                TextButton(onDismiss) { Text("Annuler", color = Palette.Text2) }
            }
        },
    )
}
