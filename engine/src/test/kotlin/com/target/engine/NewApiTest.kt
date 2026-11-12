package com.target.engine

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class NewApiTest {
    private val svc = """
    {"items":[{"content_source":"search","favourite_count":7,"id":7712345678,"is_favourite":false,
      "item_box":{"accessibility_label":"Pull torsadé Ralph Lauren, Ralph Lauren, M · Très bon état, 10,00 €, 11,20 € inclut la Protection acheteurs",
        "first_line":"Ralph Lauren","second_line":"M · Très bon état","item_id":7712345678},
      "photo":{"full_size_url":"https://images1.vinted.net/t/full.jpg","url":"https://images1.vinted.net/t/f800.jpg","high_resolution":{"id":"x","orientation":null}},
      "price":{"amount":"10.0","currency_code":"EUR"},"title":"Pull torsadé Ralph Lauren","url":"/items/7712345678-pull-torsade",
      "user":{"id":1,"login":"lea"}},
     {"id":7712345000,"item_box":{"accessibility_label":"Jean Levi's 501, Levi's, Bon état, 15,50 €","first_line":"Levi's","second_line":"Bon état"},
      "photo":{"url":"https://images1.vinted.net/t/b.jpg"}}],
     "pagination":{"current_page":1}}
    """.trimIndent()

    @Test fun parsesSvcCatalogueShape() {
        val items = VintedJson.parseCatalog(svc, "www.vinted.fr")
        assertEquals(2, items.size)
        val a = items[0]
        assertEquals("Pull torsadé Ralph Lauren", a.title)
        assertEquals("Ralph Lauren", a.brand)
        assertEquals("M", a.size)
        assertEquals(Condition.VERY_GOOD, a.condition)
        assertEquals("https://www.vinted.fr/items/7712345678-pull-torsade", a.url)
        assertEquals(10.0, a.price, 0.0)
        assertNull(a.createdAt)
        val b = items[1]
        assertEquals("Jean Levi's 501", b.title)
        assertEquals(15.5, b.price, 0.0)
        assertEquals("Levi's", b.brand)
        assertNull(b.size)
        assertEquals(Condition.GOOD, b.condition)
        assertEquals("https://www.vinted.fr/items/7712345000", b.url)
    }

    @Test fun webPageCardsAreParsed() {
        val json = """[{"id":"123","href":"https://www.vinted.fr/items/123-pull","img":"https://i/1.jpg",
          "alt":"Pull torsadé, laine, marque: Ralph Lauren, état: Très bon état, taille: M, 10,00 €, 11,20 € inclut la Protection acheteurs"},
          {"id":"124","alt":"Hoodie, brand: Stüssy, condition: New with tags, size: L, £45.00"},
          {"id":"125","alt":"nothing useful"}]"""
        val l = HtmlCards.parse(json, "www.vinted.fr")
        assertEquals(2, l.size)
        assertEquals("Pull torsadé, laine", l[0].title)
        assertEquals("Ralph Lauren", l[0].brand)
        assertEquals("M", l[0].size)
        assertEquals(10.0, l[0].price, 0.0)
        assertEquals(11.2, l[0].totalPrice!!, 1e-9)
        assertEquals(Condition.VERY_GOOD, l[0].condition)
        assertEquals("GBP", l[1].currency)
        assertEquals(45.0, l[1].price, 0.0)
        assertEquals(Condition.NEW_WITH_TAGS, l[1].condition)
    }

    @Test fun amountsInEveryFormat() {
        assertEquals(1234.5, VintedJson.parseAmount("1 234,50")!!, 1e-9)
        assertEquals(1234.5, VintedJson.parseAmount("1,234.50")!!, 1e-9)
        assertEquals(12.0, VintedJson.parseAmount("12")!!, 1e-9)
        assertEquals(12.5, VintedJson.parseAmount("12,5")!!, 1e-9)
    }

    @Test fun idClockEstimatesAges() {
        val c = IdClock()
        assertEquals(IdClock.DEFAULT_RATE, c.rate(), 0.0)
        c.record(1000, 1_000_000)
        c.record(1000 + 3600, 1_000_000 + 3600 * 50)
        assertEquals(50.0, c.rate(), 1e-9)
        assertEquals(10_000L - 600, c.estimate(1_000_000 + 3600 * 50 - 30_000, 1_000_000 + 3600 * 50, 10_000))
        val annotated = c.annotate(listOf(Listing(10, "a", 1.0, url = "u"), Listing(5, "b", 1.0, url = "u", createdAt = 42)), 100)
        assertTrue(annotated[0].ageEstimated)
        assertEquals(100L, annotated[0].createdAt)
        assertEquals(42L, annotated[1].createdAt)
        assertFalse(annotated[1].ageEstimated)
    }

    private fun jwt(payload: String) = "x." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray()) + ".sig"

    @Test fun sessionTokens() {
        assertTrue(Session.isMember(jwt("""{"scope":"user","sub":"123"}""")))
        assertTrue(Session.isMember(jwt("""{"account_id":987,"aud":"fr.core.api"}""")))
        assertFalse(Session.isMember(jwt("""{"scope":"public","sub":"0"}""")))
        assertFalse(Session.isMember(jwt("""{"aud":"fr.core.api"}""")))
        assertFalse(Session.isMember("garbage"))
        assertEquals("abc", Session.cookie("a=1; access_token_web=abc; b=2", "access_token_web"))
        assertEquals("fr-FR", VintedDomain.FR.locale)
        assertEquals("en-GB", VintedDomain.UK.locale)
        assertEquals("api.vinted.co.uk", VintedDomain.UK.apiHost)
    }
}
