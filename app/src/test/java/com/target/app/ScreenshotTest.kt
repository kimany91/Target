package com.target.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import com.target.app.data.ImageAnalysis
import com.target.app.data.RefImage
import com.target.app.data.StoredDeal
import com.target.app.search.SearchState
import com.target.app.ui.AppViewModel
import com.target.app.ui.screens.DealsScreen
import com.target.app.ui.screens.DetailScreen
import com.target.app.ui.screens.RefScreen
import com.target.app.ui.screens.SearchScreen
import com.target.app.ui.screens.SettingsScreen
import com.target.app.ui.theme.Haptics
import com.target.app.ui.theme.LocalHaptics
import com.target.app.ui.theme.TargetTheme
import com.target.engine.Condition
import com.target.engine.Lang
import com.target.engine.Listing
import com.target.engine.Planner
import com.target.engine.Ranker
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Renders every screen with realistic data and writes PNGs to app/build/screens (visual review). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<TargetApp>()
    private val out = File("build/screens").apply { mkdirs() }
    private val now = System.currentTimeMillis() / 1000

    private fun photo(name: String, c1: Int, c2: Int): String {
        val f = File(app.cacheDir, "$name.png")
        val b = Bitmap.createBitmap(300, 380, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        cv.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 300f, 380f, c1, c2, Shader.TileMode.CLAMP) })
        cv.drawRoundRect(70f, 80f, 230f, 330f, 40f, 40f, Paint().apply { color = 0x33FFFFFF })
        FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f.toURI().toString()
    }

    private fun listings(): List<Listing> {
        var id = 100L
        fun l(t: String, p: Double, b: String, size: String, ageMin: Int, cond: Condition, c1: Int, c2: Int, favs: Int = 3) =
            Listing(id++, t, p, brand = b, size = size, condition = cond, photoUrl = photo("p$id", c1, c2),
                url = "https://www.vinted.fr/items/$id", createdAt = now - ageMin * 60, favourites = favs, seller = "juliette_vtg")
        val market = (0 until 14).map { l("Pull Ralph Lauren col rond", 28.0 + it * 3, "Ralph Lauren", "M", 600 + it * 120, Condition.VERY_GOOD, 0xFF1C2541.toInt(), 0xFF3A506B.toInt()) }
        return market + listOf(
            l("Pull torsadé Ralph Lauren crème", 10.0, "Polo Ralph Lauren", "M", 4, Condition.VERY_GOOD, 0xFFEDE3CF.toInt(), 0xFFB8A988.toInt(), 9),
            l("Strickpullover Ralph Lauren navy", 19.0, "Ralph Lauren", "L", 25, Condition.NEW, 0xFF14213D.toInt(), 0xFF2B3A67.toInt()),
            l("Maglione Ralph Lauren verde", 24.0, "Ralph Lauren", "S", 95, Condition.GOOD, 0xFF2E7D32.toInt(), 0xFF1B4D20.toInt()),
            l("Pull Ralph Lauren cachemire gris", 35.0, "Ralph Lauren", "M", 180, Condition.NEW_WITH_TAGS, 0xFF8E8E8E.toInt(), 0xFF5A5A5A.toInt()),
        )
    }

    private fun vm(): AppViewModel {
        val c = app.container
        val p = c.profiles.active()!!
        val ref = RefImage("r1", File(java.net.URI(photo("ref", 0xFFEDE3CF.toInt(), 0xFFB09A74.toInt()))).absolutePath, 0,
            ImageAnalysis(labels = listOf("Sweater", "Wool"), items = listOf("knit"), materials = listOf("wool"), colors = listOf("cream", "beige"), brands = listOf("ralphlauren")))
        c.profiles.update(p.id) { it.copy(keywords = listOf("Old money", "Ralph Lauren", "Pull / Maille", "Cachemire", "harrington"), images = listOf(ref), sizes = listOf("M", "L"), maxPrice = 60.0) }
        if (c.profiles.profiles.value.size == 1) c.profiles.create("Ma sœur", "🦋", 280f)
        c.profiles.setActive(p.id)
        val vm = AppViewModel(app)
        val ranked = Ranker(Planner.targetsForSearch("knit ralph lauren"), nowSeconds = now).rank(listings())
        vm.showResults(SearchState(false, 32, 32, null, 812, Lang.entries.toSet(), ranked, 0))
        vm.searchLabel.value = "« knit ralph lauren »"
        c.deals.add(ranked.filter { it.deal?.isDeal == true }.map { StoredDeal(it, p.id, p.name, System.currentTimeMillis()) })
        return vm
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { rule.activity.actionBar?.hide() }
        rule.setContent {
            val view = LocalView.current
            TargetTheme {
                CompositionLocalProvider(LocalHaptics provides Haptics(view) { false }) {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.background(com.target.app.ui.theme.Palette.Bg)) { content() }
                }
            }
        }
        repeat(10) { rule.mainClock.advanceTimeBy(250); Thread.sleep(120); shadowOf(android.os.Looper.getMainLooper()).idle() }
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        FileOutputStream(File(out, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun refs() { val vm = vm(); shot("1_refs") { RefScreen(vm) } }
    @Test fun search() { val vm = vm(); shot("2_search") { SearchScreen(vm) {} } }
    @Test fun deals() { val vm = vm(); shot("3_deals") { DealsScreen(vm) {} } }
    @Test fun settings() { val vm = vm(); shot("4_settings") { SettingsScreen(vm) {} } }
    @Test fun detail() {
        val vm = vm()
        val id = vm.search.value!!.results.first().listing.id
        shot("5_detail") { DetailScreen(vm, id) {} }
    }
}
