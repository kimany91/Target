package com.target.engine

enum class Tier { FAST, MID, PREMIUM, LUXURY }

/**
 * A brand known by the engine.
 * @param base typical second-hand price (EUR) of a "standard top" (sweatshirt-like piece) in good condition on Vinted.
 * @param titleSafe false when the name is also a common word ("only", "champion"...): it is then trusted only in
 *                  Vinted's brand field, never inside a title.
 */
class Brand(val name: String, aliases: List<String>, val base: Double, val titleSafe: Boolean = true) {
    val id: String = Text.normalize(name).replace(" ", "")
    val tier: Tier = when {
        base >= 60 -> Tier.LUXURY
        base >= 20 -> Tier.PREMIUM
        base >= 10 -> Tier.MID
        else -> Tier.FAST
    }
    /** Normalised alias phrases (the canonical name first). */
    val phrases: List<List<String>> = (listOf(name) + aliases).map { Text.tokens(it) }.filter { it.isNotEmpty() }.distinct()
    /** Single-string forms without spaces, used for fuzzy matching ("ralphlauren"). */
    val glued: List<String> = phrases.map { it.joinToString("") }.distinct()

    override fun toString() = name
}

object Brands {
    private fun b(name: String, base: Double, vararg aliases: String, titleSafe: Boolean = true) = Brand(name, aliases.toList(), base, titleSafe)

    val all: List<Brand> = listOf(
        // Luxury / designer
        b("Gucci", 120.0), b("Louis Vuitton", 200.0, "lv", "vuitton"), b("Prada", 110.0), b("Dior", 150.0, "christian dior"),
        b("Chanel", 200.0), b("Saint Laurent", 120.0, "ysl", "yves saint laurent"), b("Balenciaga", 110.0), b("Burberry", 70.0, "burberrys"),
        b("Moncler", 120.0), b("Loro Piana", 140.0), b("Brunello Cucinelli", 120.0, "cucinelli"), b("Hermès", 200.0, "hermes paris"),
        b("Celine", 120.0, "céline"), b("Bottega Veneta", 150.0), b("Fendi", 120.0), b("Givenchy", 90.0), b("Valentino", 100.0),
        b("Versace", 70.0), b("Stone Island", 70.0), b("Canada Goose", 150.0), b("Off-White", 70.0, "off white"),
        b("Maison Margiela", 90.0, "margiela", "mm6"), b("Rick Owens", 120.0, "drkshdw"), b("Acne Studios", 60.0, "acne"),
        b("Max Mara", 80.0, "maxmara"), b("The Row", 200.0, titleSafe = false), b("Miu Miu", 120.0), b("Loewe", 150.0),
        b("Thom Browne", 120.0), b("Isabel Marant", 70.0, "isabel marant etoile"), b("Kapital", 120.0), b("Visvim", 150.0),
        b("Needles", 90.0), b("Yohji Yamamoto", 150.0, "yohji", "y3", "y-3"), b("Comme des Garçons", 80.0, "cdg", "comme des garcons play"),
        b("Issey Miyake", 100.0, "pleats please", "homme plisse"), b("Helmut Lang", 80.0), b("Raf Simons", 120.0), b("Bape", 60.0, "a bathing ape"),
        b("Golden Goose", 120.0, "ggdb"), b("Common Projects", 120.0), b("Church's", 120.0, "churchs"), b("J.M. Weston", 200.0, "jm weston", "weston"),
        b("Paraboot", 120.0), b("Red Wing", 120.0, "red wing shoes"), b("Belstaff", 80.0), b("Woolrich", 70.0), b("Tod's", 90.0, "tods"),
        b("Polène", 150.0, "polene"), b("Our Legacy", 70.0), b("Jacquemus", 60.0), b("Mackage", 120.0), b("Parajumpers", 80.0),
        b("Moose Knuckles", 100.0), b("Barbour", 60.0, "barbour international"), b("Arc'teryx", 90.0, "arcteryx", "arc teryx"),
        b("CP Company", 60.0, "c.p. company", "c p company"),
        // Premium
        b("Ralph Lauren", 30.0, "polo ralph lauren", "lauren ralph lauren", "polo by ralph lauren", "rl", "ralph", "polo sport", "rrl"),
        b("Lacoste", 25.0), b("Tommy Hilfiger", 20.0, "tommy", "tommy jeans", "hilfiger"), b("Hugo Boss", 25.0, "boss", "hugo", titleSafe = true),
        b("Gant", 22.0), b("Hackett", 25.0, "hackett london"), b("Brooks Brothers", 25.0), b("Massimo Dutti", 22.0, "dutti"),
        b("The North Face", 35.0, "north face", "tnf"), b("Patagonia", 40.0), b("Carhartt", 30.0, "carhartt wip"), b("Stüssy", 35.0, "stussy"),
        b("Supreme", 45.0), b("Palace", 45.0, "palace skateboards"), b("Corteiz", 50.0, "crtz"), b("A.P.C.", 40.0, "apc"),
        b("Sézane", 35.0, "sezane"), b("Ganni", 40.0), b("COS", 22.0, titleSafe = false), b("Arket", 22.0), b("Diesel", 20.0),
        b("Napapijri", 25.0), b("Fred Perry", 25.0), b("Lululemon", 30.0), b("Alo Yoga", 30.0, "alo"), b("Salomon", 50.0),
        b("New Balance", 30.0, "nb"), b("Jordan", 30.0, "air jordan", "nike jordan"), b("Dr. Martens", 45.0, "dr martens", "doc martens", "docs martens"),
        b("Timberland", 35.0), b("Clarks", 30.0, "clarks originals"), b("Birkenstock", 35.0), b("UGG", 35.0), b("Veja", 35.0),
        b("Asics", 25.0), b("On Running", 40.0, "on cloud", "on cloudmonster"), b("Hoka", 40.0, "hoka one one"), b("Columbia", 20.0),
        b("Helly Hansen", 25.0), b("Paul Smith", 35.0), b("Kenzo", 40.0), b("Moschino", 45.0), b("Michael Kors", 30.0, "mk"),
        b("AMI Paris", 45.0, "ami", "ami de coeur"), b("Maje", 45.0), b("Sandro", 45.0), b("The Kooples", 40.0, "kooples"),
        b("Zadig & Voltaire", 45.0, "zadig et voltaire", "zadig"), b("Claudie Pierlot", 35.0), b("Ba&sh", 45.0, "bash", "ba sh"),
        b("Rouje", 40.0), b("Reformation", 45.0), b("Free People", 30.0), b("Saint James", 30.0), b("Armor Lux", 20.0, "armor-lux"),
        b("Aigle", 30.0), b("Norse Projects", 45.0), b("Wood Wood", 30.0), b("Filippa K", 40.0), b("Samsøe Samsøe", 25.0, "samsoe samsoe", "samsoe"),
        b("Schott", 60.0, "schott nyc"), b("Alpha Industries", 40.0), b("Longchamp", 45.0), b("Coach", 50.0), b("Ray-Ban", 45.0, "rayban", "ray ban"),
        b("Evisu", 45.0), b("True Religion", 25.0), b("Ed Hardy", 25.0), b("Von Dutch", 25.0), b("Juicy Couture", 20.0, "juicy"),
        b("Sergio Tacchini", 20.0), b("Abercrombie & Fitch", 20.0, "abercrombie", "a&f"), b("Everlane", 25.0), b("Sebago", 35.0),
        b("Hogan", 60.0), b("Axel Arigato", 60.0), b("Autry", 50.0), b("Onitsuka Tiger", 35.0, "onitsuka"), b("Merrell", 25.0),
        b("Thrasher", 20.0), b("Oakley", 25.0), b("Mizuno", 25.0), b("Saucony", 25.0), b("Diadora", 20.0), b("Calvin Klein", 20.0, "ck", "calvin klein jeans"),
        b("Guess", 15.0, titleSafe = false), b("Comptoir des Cotonniers", 25.0), b("Vivienne Westwood", 60.0),
        // Mid
        b("Nike", 18.0, "nike sportswear", "nsw", "nike acg", "acg"), b("Adidas", 15.0, "adidas originals"), b("Levi's", 18.0, "levis", "levi strauss"),
        b("Dickies", 18.0), b("Puma", 10.0), b("Reebok", 12.0), b("Converse", 15.0), b("Vans", 15.0), b("Kappa", 12.0), b("Umbro", 12.0),
        b("Champion", 12.0, titleSafe = false), b("Fila", 10.0), b("Ellesse", 12.0), b("Le Coq Sportif", 12.0), b("Element", 12.0, titleSafe = false),
        b("Santa Cruz", 15.0), b("Miss Sixty", 15.0), b("Superdry", 15.0), b("Lee", 15.0, titleSafe = false), b("Wrangler", 15.0),
        b("G-Star", 15.0, "g star raw", "gstar"), b("Pepe Jeans", 12.0), b("Desigual", 12.0), b("Benetton", 10.0, "united colors of benetton"),
        b("Gymshark", 12.0), b("Under Armour", 10.0), b("Quiksilver", 10.0), b("Billabong", 10.0), b("Rip Curl", 10.0), b("Volcom", 12.0),
        b("Obey", 15.0), b("HUF", 15.0), b("Kangol", 15.0), b("New Era", 12.0), b("Crocs", 15.0), b("Faguo", 15.0), b("Petit Bateau", 10.0),
        b("Eastpak", 15.0), b("Uniqlo", 10.0), b("Hollister", 10.0), b("Mango", 9.0, titleSafe = false),
        // Fast fashion
        b("Zara", 7.0), b("H&M", 4.0, "hm", "h and m", "h et m"), b("Bershka", 5.0), b("Pull&Bear", 5.0, "pull and bear", "pull bear"),
        b("Stradivarius", 5.0), b("Shein", 3.0), b("Primark", 3.0), b("Kiabi", 3.0), b("Jules", 5.0, titleSafe = false), b("Celio", 5.0),
        b("Jack & Jones", 6.0, "jack and jones", "jack jones"), b("Only", 5.0, titleSafe = false), b("Vero Moda", 5.0),
        b("Bonobo", 6.0), b("Springfield", 5.0), b("C&A", 4.0, "c and a"), b("Asos", 6.0), b("Topshop", 7.0), b("Boohoo", 4.0),
    )

    private val byId = all.associateBy { it.id }
    fun byId(id: String): Brand? = byId[id]
    fun byName(name: String): Brand? = byId[Text.normalize(name).replace(" ", "")] ?: all.firstOrNull { b -> b.glued.contains(Text.normalize(name).replace(" ", "")) }

    /** Phrase index for greedy parsing: longest phrases first. */
    val phraseIndex: List<Pair<List<String>, Brand>> =
        all.flatMap { b -> b.phrases.map { it to b } }.sortedByDescending { it.first.size }

    /** Find which known brand Vinted's own brand field designates (e.g. "Polo Ralph Lauren" → Ralph Lauren). */
    fun fromBrandField(brandTitle: String?): Brand? {
        if (brandTitle.isNullOrBlank()) return null
        val t = Text.tokens(brandTitle)
        if (t.isEmpty()) return null
        val glued = t.joinToString("")
        // exact glued equality first (handles "H&M", "Levi's")
        all.firstOrNull { it.glued.contains(glued) }?.let { return it }
        return phraseIndex.firstOrNull { (p, _) -> Text.containsPhrase(t, p) }?.second
    }

    /** Brand mentioned inside a free title. Ambiguous names (titleSafe=false) are ignored here. */
    fun inTitle(titleTokens: List<String>): Brand? =
        phraseIndex.firstOrNull { (p, b) -> b.titleSafe && (p.size > 1 || p[0].length >= 3) && Text.containsPhrase(titleTokens, p) }?.second
}
