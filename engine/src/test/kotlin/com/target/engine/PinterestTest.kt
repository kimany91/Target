package com.target.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinterestTest {
    private fun res(name: String) = javaClass.classLoader.getResource(name)!!.readText()

    @Test fun parsesARealSearchPage() {
        val page = PinterestJson.parse(res("pinterest_search.json"))   // trimmed answer of the live endpoint
        assertEquals(6, page.pins.size)                                   // stories are skipped
        assertNotNull(page.bookmark)
        val p = page.pins.first()
        assertTrue(p.imageUrl.startsWith("https://i.pinimg.com/736x/"))
        assertTrue(p.thumbUrl.startsWith("https://i.pinimg.com/474x/"))
        assertTrue(p.height > p.width)
        assertTrue(p.color!!.startsWith("#"))
    }

    @Test fun garbageIsEmpty() {
        assertTrue(PinterestJson.parse("<html>blocked</html>").pins.isEmpty())
        assertTrue(PinterestJson.parse("""{"resource_response":{"status":"failure","data":null}}""").pins.isEmpty())
    }

    @Test fun urlCarriesQueryAndBookmark() {
        val u = PinterestJson.searchUrl("knit streetwear", "BM1")
        assertTrue(u.startsWith("https://www.pinterest.com/resource/BaseSearchResource/get/?source_url=%2Fsearch%2Fpins%2F%3Fq%3Dknit%2520streetwear"))
        val data = java.net.URLDecoder.decode(u.substringAfter("&data="), "UTF-8")
        assertTrue(data, data.contains("\"query\":\"knit streetwear\"") && data.contains("\"bookmarks\":[\"BM1\"]"))
    }
}
