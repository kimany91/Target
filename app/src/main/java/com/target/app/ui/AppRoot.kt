package com.target.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.target.app.ui.components.LocalSharedScope
import com.target.app.ui.components.ProvideNavScope
import com.target.app.ui.components.bouncyClick
import com.target.app.ui.screens.DealsScreen
import com.target.app.ui.screens.DetailScreen
import com.target.app.ui.screens.LoginScreen
import com.target.app.ui.screens.RefScreen
import com.target.app.ui.screens.SearchScreen
import com.target.app.ui.screens.SettingsScreen
import com.target.app.ui.theme.LocalHaptics
import com.target.app.ui.theme.Palette
import com.target.app.ui.theme.TargetTheme
import com.target.app.ui.theme.rememberHaptics
import com.target.app.update.UpdateState

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppRoot(vm: AppViewModel) {
    TargetTheme {
        val settings by vm.settings.collectAsState()
        val haptics = rememberHaptics { vm.settings.value.haptics }
        val nav = rememberNavController()

        val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33 && settings.dealsEnabled) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        LaunchedEffect(Unit) {
            vm.nav.collect { e -> if (e is NavEvent.OpenItem) nav.navigate("item/${e.id}") { launchSingleTop = true } }
        }

        CompositionLocalProvider(LocalHaptics provides haptics) {
            SharedTransitionLayout(Modifier.fillMaxSize().background(Palette.Bg)) {
                CompositionLocalProvider(LocalSharedScope provides this) {
                    NavHost(
                        nav, startDestination = "home",
                        enterTransition = { fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.96f) },
                        exitTransition = { fadeOut(tween(250)) },
                        popEnterTransition = { fadeIn(tween(300)) },
                        popExitTransition = { fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 0.96f) },
                    ) {
                        composable("home") {
                            ProvideNavScope(this) {
                                Home(vm, onOpen = { id -> nav.navigate("item/$id") }, onLogin = { nav.navigate("login") })
                            }
                        }
                        composable("item/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                            ProvideNavScope(this) {
                                DetailScreen(vm, entry.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
                            }
                        }
                        composable("login") {
                            LoginScreen(vm.settings.value.home.host, isMember = { vm.isMemberNow() }, onDone = { vm.refreshVintedUser(); nav.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Home(vm: AppViewModel, onOpen: (Long) -> Unit, onLogin: () -> Unit) {
    val tab by vm.tab.collectAsState()
    val update by vm.update.collectAsState()
    val deals by vm.deals.collectAsState()
    val h = LocalHaptics.current
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it / 5 * dir } + fadeIn(tween(220)))
                    .togetherWith(slideOutHorizontally(tween(220)) { -it / 6 * dir } + fadeOut(tween(160)))
            },
            label = "tabs",
        ) { t ->
            when (t) {
                Tab.REFS -> RefScreen(vm)
                Tab.SEARCH -> SearchScreen(vm, onOpen, onLogin)
                Tab.DEALS -> DealsScreen(vm, onOpen)
                Tab.SETTINGS -> SettingsScreen(vm, onLogin)
            }
        }
        // scrim behind the status bar so scrolled content never collides with the clock/icons
        Box(
            Modifier.fillMaxWidth().align(Alignment.TopCenter)
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Palette.Bg, Palette.Bg.copy(alpha = 0.85f), Color.Transparent)))
                .statusBarsPadding().height(14.dp)
        )
        UpdateBanner(update, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp)) { vm.installUpdate() }
        BottomBar(tab, deals.size, Modifier.align(Alignment.BottomCenter)) { if (it != tab) { h.tick(); vm.tab.value = it } }
        val prompt by vm.connectPrompt.collectAsState()
        ConnectPrompt(prompt, onLogin = { vm.dismissConnectPrompt(); onLogin() }, onLater = { vm.dismissConnectPrompt() })
    }
}

private data class TabSpec(val tab: Tab, val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabSpec(Tab.REFS, "Réfs", Icons.Rounded.AutoAwesome),
    TabSpec(Tab.SEARCH, "Recherche", Icons.Rounded.Search),
    TabSpec(Tab.DEALS, "Deals", Icons.Rounded.LocalFireDepartment),
    TabSpec(Tab.SETTINGS, "Réglages", Icons.Rounded.Tune),
)

/** Floating glass navigation pill with a springy sliding indicator. */
@Composable
private fun BottomBar(current: Tab, dealCount: Int, modifier: Modifier, onSelect: (Tab) -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Palette.Bg.copy(alpha = 0.92f), Palette.Bg)))
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, bottom = 10.dp, top = 18.dp)
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(66.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Palette.Surface.copy(alpha = 0.96f))
                .border(1.dp, Palette.Outline, RoundedCornerShape(26.dp))
                .padding(6.dp)
        ) {
            val w = maxWidth / TABS.size
            val x by animateDpAsState(w * current.ordinal, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow), label = "ind")
            Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(Palette.Surface3))
            Row(Modifier.fillMaxSize()) {
                TABS.forEach { spec ->
                    val selected = spec.tab == current
                    val scale by animateFloatAsState(if (selected) 1.12f else 1f, spring(dampingRatio = 0.5f), label = "s")
                    Column(
                        Modifier.weight(1f).fillMaxHeight().bouncyClick(haptic = false) { onSelect(spec.tab) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Box {
                            Icon(spec.icon, spec.label, tint = if (selected) Palette.Accent else Palette.Text3,
                                modifier = Modifier.size(24.dp).graphicsLayer { scaleX = scale; scaleY = scale })
                            if (spec.tab == Tab.DEALS && dealCount > 0) Box(
                                Modifier.align(Alignment.TopEnd).offset(x = 7.dp, y = (-4).dp).size(16.dp).clip(CircleShape).background(Palette.Deal),
                                contentAlignment = Alignment.Center,
                            ) { Text(if (dealCount > 9) "9+" else "$dealCount", style = MaterialTheme.typography.labelSmall, color = Color.Black) }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(spec.label, style = MaterialTheme.typography.labelMedium, color = if (selected) Palette.Text else Palette.Text3)
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateBanner(state: UpdateState, modifier: Modifier, onClick: () -> Unit) {
    val visible = state is UpdateState.Available || state is UpdateState.Downloading || state is UpdateState.NeedsPermission ||
        state is UpdateState.Installing || state is UpdateState.ManualInstall || (state is UpdateState.Failed && state.message.startsWith("Mise à jour"))
    AnimatedVisibility(visible, modifier, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.Surface2).border(1.dp, Palette.Outline, RoundedCornerShape(18.dp))
                .bouncyClick(onClick = onClick).padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SystemUpdate, null, tint = Palette.Accent)
                Spacer(Modifier.width(10.dp))
                val text = when (state) {
                    is UpdateState.Available -> "Nouvelle version ${state.info.version} — toucher pour installer"
                    is UpdateState.Downloading -> "Mise à jour ${state.info.version} · ${(state.progress * 100).toInt()} %"
                    is UpdateState.NeedsPermission -> "Autorise Target à s'installer pour passer en ${state.info.version}"
                    is UpdateState.Installing -> "Installation de la ${state.info.version}…"
                    is UpdateState.ManualInstall -> "Appuie sur « Mettre à jour » dans la fenêtre Android pour passer en ${state.info.version} (toucher pour la rouvrir)"
                    is UpdateState.Failed -> "${state.message} — toucher pour réessayer"
                    else -> ""
                }
                Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = Palette.Text)
            }
            if (state is UpdateState.Downloading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)), color = Palette.Accent, trackColor = Palette.Surface3)
            }
        }
    }
}

/** Full-screen invitation to connect a Vinted account, shown while the user is not logged in. */
@Composable
private fun ConnectPrompt(visible: Boolean, onLogin: () -> Unit, onLater: () -> Unit) {
    val h = LocalHaptics.current
    AnimatedVisibility(visible, enter = fadeIn(tween(250)), exit = fadeOut(tween(200))) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)).bouncyClick(haptic = false, scale = 1f) {},
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(visible, enter = slideInVertically(spring(dampingRatio = 0.8f)) { it } + fadeIn(), exit = slideOutVertically { it }) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(14.dp)
                        .clip(RoundedCornerShape(30.dp)).background(Palette.Surface)
                        .border(1.dp, Palette.Outline, RoundedCornerShape(30.dp)).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    com.target.app.ui.components.TargetRadar(Modifier.size(96.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Connecte ton Vinted", style = MaterialTheme.typography.headlineMedium, color = Palette.Text)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Target cherche sur Vinted avec ta session : connecte-toi une fois pour des recherches fiables, la fiche complète des articles et des alertes Deal qui ne sont pas bloquées.",
                        style = MaterialTheme.typography.bodyMedium, color = Palette.Text2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "🔒 Tu te connectes directement sur le site de Vinted : Target ne voit jamais ton mot de passe. Utilise ton e-mail (la connexion Google n'est pas autorisée dans une app tierce).",
                        style = MaterialTheme.typography.bodySmall, color = Palette.Text3,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    com.target.app.ui.components.GradientButton("Se connecter à Vinted", Modifier.fillMaxWidth()) { onLogin() }
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.material3.TextButton({ h.tick(); onLater() }) { Text("Plus tard", color = Palette.Text2) }
                }
            }
        }
    }
}
