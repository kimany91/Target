package com.target.app.ui.screens

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.target.app.BuildConfig
import com.target.app.ui.AppViewModel
import com.target.app.ui.components.bouncyClick
import com.target.app.ui.theme.LocalHaptics
import com.target.app.ui.theme.Palette
import com.target.app.update.UpdateState
import com.target.engine.Sensitivity
import com.target.engine.VintedDomain

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onLogin: () -> Unit) {
    val s by vm.settings.collectAsState()
    val user by vm.vintedUser.collectAsState()
    val update by vm.update.collectAsState()
    val h = LocalHaptics.current
    var showCountries by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 130.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Réglages", style = MaterialTheme.typography.displaySmall, color = Palette.Text, modifier = Modifier.statusBarsPadding().padding(top = 18.dp, start = 4.dp))

        Group("Compte Vinted", Icons.Rounded.AccountCircle) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (user != null) "Connecté · $user" else "Non connecté", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
                    Text("La recherche fonctionne sans compte. Te connecter rend la session Vinted plus stable.", style = MaterialTheme.typography.bodySmall, color = Palette.Text2)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row {
                ActionChip(if (user != null) "Changer de compte" else "Se connecter", Palette.Accent) { onLogin() }
                Spacer(Modifier.width(8.dp))
                if (user != null) ActionChip("Déconnexion", Palette.Text3) { h.reject(); vm.logoutVinted() }
            }
        }

        Group("Diagnostic Vinted", Icons.Rounded.NetworkCheck) {
            val test by vm.selfTest.collectAsState()
            val log by vm.vintedLog.collectAsState()
            Text("Vérifie que Target arrive à chercher sur Vinted depuis ton téléphone.", style = MaterialTheme.typography.bodySmall, color = Palette.Text2)
            Spacer(Modifier.height(10.dp))
            ActionChip("Tester la connexion", Palette.Info) { vm.runSelfTest() }
            test?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.titleSmall, color = if (it.startsWith("✓")) Palette.Deal else if (it.startsWith("✗")) Palette.Accent else Palette.Text2)
            }
            if (log.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    log.takeLast(10).joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    color = Palette.Text3,
                )
            }
        }

        Group("Pays & langues", Icons.Rounded.Language) {
            Text("Site principal", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
            Text("Vinted partage son catalogue en Europe : Target y cherche dans les 18 langues.", style = MaterialTheme.typography.bodySmall, color = Palette.Text2)
            Spacer(Modifier.height(8.dp))
            val shown = if (showCountries) VintedDomain.entries else VintedDomain.entries.take(6)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                shown.forEach { d -> Choice(d.label, d == s.home) { vm.updateSettings { it.copy(home = d, extraDomains = it.extraDomains - d) } } }
            }
            TextButton({ showCountries = !showCountries }) { Text(if (showCountries) "Moins de pays" else "Tous les pays", color = Palette.Text2) }
            Text("Sites supplémentaires (plus lent)", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VintedDomain.entries.filter { it != s.home && (showCountries || it in listOf(VintedDomain.DE, VintedDomain.IT, VintedDomain.ES, VintedDomain.NL, VintedDomain.PL, VintedDomain.UK)) }.forEach { d ->
                    Choice(d.label, d in s.extraDomains) {
                        vm.updateSettings { st -> st.copy(extraDomains = if (d in st.extraDomains) st.extraDomains - d else st.extraDomains + d) }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            ToggleRow("Recherche approfondie", "Plus de variantes par recherche (plus complet, un peu plus long)", s.deepSearch) { v -> vm.updateSettings { it.copy(deepSearch = v) } }
        }

        Group("Deals", Icons.Rounded.LocalFireDepartment) {
            ToggleRow("Notifications de deal", "Scan en arrière-plan de tous les profils", s.dealsEnabled) { v -> vm.updateSettings { it.copy(dealsEnabled = v) } }
            Spacer(Modifier.height(10.dp))
            Text("Sensibilité", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
            Text(
                when (s.sensitivity) {
                    Sensitivity.STRICT -> "Seulement les prix vraiment exceptionnels"
                    Sensitivity.NORMAL -> "Bon équilibre : ni trop, ni trop peu d'alertes"
                    Sensitivity.WIDE -> "Plus d'alertes, y compris les très bonnes affaires"
                }, style = MaterialTheme.typography.bodySmall, color = Palette.Text2,
            )
            Spacer(Modifier.height(8.dp))
            Segmented(Sensitivity.entries.map { it.label }, Sensitivity.entries.indexOf(s.sensitivity)) { i -> vm.updateSettings { it.copy(sensitivity = Sensitivity.entries[i]) } }
            Spacer(Modifier.height(12.dp))
            Text("Fréquence du scan", style = MaterialTheme.typography.titleSmall, color = Palette.Text)
            Spacer(Modifier.height(8.dp))
            val freqs = listOf(15, 30, 60, 180)
            Segmented(freqs.map { if (it < 60) "$it min" else "${it / 60} h" }, freqs.indexOf(s.scanMinutes).coerceAtLeast(0)) { i -> vm.updateSettings { it.copy(scanMinutes = freqs[i]) } }
            Spacer(Modifier.height(10.dp))
            ToggleRow("Silence la nuit", "Pas de notification entre 23 h et 8 h", s.quietNight) { v -> vm.updateSettings { it.copy(quietNight = v) } }
        }

        Group("Expérience", Icons.Rounded.Vibration) {
            ToggleRow("Retours haptiques", "Vibrations subtiles pendant la navigation", s.haptics) { v -> vm.updateSettings { it.copy(haptics = v) }; if (v) h.confirm() }
        }

        Group("Mises à jour", Icons.Rounded.SystemUpdate) {
            ToggleRow("Mise à jour automatique", "Vérifie à chaque démarrage et installe la nouvelle version", s.autoUpdate) { v -> vm.updateSettings { it.copy(autoUpdate = v) } }
            Spacer(Modifier.height(10.dp))
            Text(
                when (val u = update) {
                    UpdateState.Idle -> "—"
                    UpdateState.Checking -> "Vérification…"
                    is UpdateState.UpToDate -> "Target est à jour"
                    is UpdateState.Available -> "Version ${u.info.version} disponible"
                    is UpdateState.Downloading -> "Téléchargement ${(u.progress * 100).toInt()} %"
                    is UpdateState.NeedsPermission -> "Autorisation d'installation requise"
                    is UpdateState.Installing -> "Installation…"
                    is UpdateState.Failed -> u.message
                },
                style = MaterialTheme.typography.bodyMedium, color = Palette.Text2,
            )
            Spacer(Modifier.height(8.dp))
            Row {
                ActionChip("Vérifier maintenant", Palette.Accent) { vm.checkUpdate() }
                Spacer(Modifier.width(8.dp))
                if (update is UpdateState.Available || update is UpdateState.NeedsPermission) ActionChip("Installer", Palette.Deal) { vm.installUpdate() }
            }
        }

        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Target", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), color = Palette.Text)
            Text("Version ${vm.currentVersion} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyMedium, color = Palette.Text2)
            Text("Application indépendante, non affiliée à Vinted.", style = MaterialTheme.typography.bodySmall, color = Palette.Text3)
        }
    }
}

@Composable
private fun Group(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.Surface).border(1.dp, Palette.Outline.copy(alpha = 0.6f), RoundedCornerShape(24.dp)).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Palette.Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = Palette.Text)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val h = LocalHaptics.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Palette.Text)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.Text2)
        }
        Switch(value, { h.toggle(it); onChange(it) }, colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent, checkedThumbColor = Color.White))
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(12.dp)).background(if (selected) Palette.Accent.copy(alpha = 0.2f) else Palette.Surface2)
            .border(1.dp, if (selected) Palette.Accent else Palette.Outline, RoundedCornerShape(12.dp)).bouncyClick(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Palette.Text else Palette.Text2) }
}

@Composable
private fun ActionChip(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.15f)).border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
            .bouncyClick(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp)
    ) { Text(label, style = MaterialTheme.typography.labelLarge, color = color) }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val h = LocalHaptics.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Surface2).padding(4.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (on) Palette.Surface3 else Color.Transparent)
                    .bouncyClick(haptic = false) { if (!on) { h.segment(); onSelect(i) } }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(o, style = MaterialTheme.typography.labelLarge, color = if (on) Palette.Text else Palette.Text3) }
        }
    }
}
