package com.target.engine

import org.junit.Assert.*
import org.junit.Test

class JsonAndUpdateTest {
    private val modern = """
    {"items":[{"id":4815162342,"title":"Pull Ralph Lauren torsadé","price":{"amount":"10.0","currency_code":"EUR"},
      "total_item_price":{"amount":"11.2","currency_code":"EUR"},"brand_title":"Polo Ralph Lauren","size_title":"M",
      "status":"Très bon état","url":"https://www.vinted.fr/items/4815162342-pull-ralph-lauren","favourite_count":4,"view_count":12,
      "user":{"login":"marie"},"photo":{"url":"https://images1.vinted.net/t/a.jpg","high_resolution":{"timestamp":1731400000}}},
      {"id":2,"title":"Jean","price":"12,5","currency":"PLN","status":"Bon état","path":"/items/2-jean"},
      {"title":"no id"}]}
    """.trimIndent()

    @Test fun parsesBothApiShapes() {
        val items = VintedJson.parseCatalog(modern, "www.vinted.fr")
        assertEquals(2, items.size)
        val a = items[0]
        assertEquals(10.0, a.price, 0.0)
        assertEquals(11.2, a.totalPrice!!, 0.0)
        assertEquals(11.2, a.buyerPrice, 0.0)
        assertEquals(Condition.VERY_GOOD, a.condition)
        assertEquals(1731400000L, a.createdAt)
        assertEquals("Polo Ralph Lauren", a.brand)
        assertEquals("marie", a.seller)
        val b = items[1]
        assertEquals(12.5, b.price, 0.0)
        assertEquals("PLN", b.currency)
        assertEquals("https://www.vinted.fr/items/2-jean", b.url)
        assertEquals(12.5 + 0.7 + 12.5 * 0.05, b.buyerPrice, 1e-9)
    }

    @Test fun garbageIsTolerated() {
        assertTrue(VintedJson.parseCatalog("<html>blocked</html>", "x").isEmpty())
        assertTrue(VintedJson.parseCatalog("{}", "x").isEmpty())
        assertNull(VintedJson.parseDetails("nope", "x"))
        val d = VintedJson.parseDetails("""{"item":{"id":7,"title":"T","price":"3","description":"Super état","photos":[{"url":"u1"},{"url":"u2"}]}}""", "www.vinted.fr")
        assertEquals("Super état", d!!.description)
        assertEquals(listOf("u1", "u2"), d.photos)
    }

    @Test fun versionsCompare() {
        assertTrue(Version.parse("v1.0.10")!! > Version.parse("1.0.9")!!)
        assertTrue(Version.parse("1.1")!! > Version.parse("1.0.99")!!)
        assertEquals(0, Version.parse("1.0")!!.compareTo(Version.parse("1.0.0")!!))
        assertNull(Version.parse("latest"))
    }

    @Test fun parsesGithubRelease() {
        val body = """{"tag_name":"v1.0.12","draft":false,"prerelease":false,"body":"notes",
          "assets":[{"name":"Target-1.0.12.apk","size":123,"browser_download_url":"https://github.com/kimany91/Target/releases/download/v1.0.12/Target-1.0.12.apk"},
                    {"name":"Target-1.0.12.apk.sha256","browser_download_url":"https://github.com/kimany91/Target/releases/download/v1.0.12/Target-1.0.12.apk.sha256"}]}"""
        val r = Releases.parseLatest(body)!!
        assertEquals("1.0.12", r.version.toString())
        assertNotNull(r.sha256Url)
        assertTrue(Releases.isNewer("1.0.11", r))
        assertFalse(Releases.isNewer("1.0.12", r))
        assertFalse(Releases.isNewer("1.1.0", r))
        // refuse non-GitHub download hosts
        assertNull(Releases.parseLatest(body.replace("https://github.com/kimany91", "https://evil.example.com/x")))
        assertNull(Releases.parseLatest("""{"tag_name":"v2.0.0","assets":[]}"""))
    }
}
