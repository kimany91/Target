package com.target.engine

/** A fashion aesthetic ("Old money", "Streetwear"...) expanded into the brands, pieces and words that define it. */
class Style(
    val id: String,
    val label: String,
    aliases: List<String>,
    val brands: List<String>,     // brand names
    val items: List<String>,      // concept ids
    val materials: List<String> = emptyList(),
    /** Words people literally write in titles for this style (searched as-is). */
    val searchWords: List<String> = emptyList(),
) {
    val phrases: List<List<String>> = (listOf(label) + aliases).map { Text.tokens(it) }.filter { it.isNotEmpty() }.distinct()
    val brandIds: List<String> get() = brands.mapNotNull { Brands.byName(it)?.id }
}

object Styles {
    val all: List<Style> = listOf(
        Style("oldmoney", "Old money", listOf("oldmoney", "old-money", "old money style", "ivy league", "ivy"),
            listOf("Ralph Lauren", "Lacoste", "Gant", "Hackett", "Brooks Brothers", "Massimo Dutti", "Barbour", "Loro Piana", "Tommy Hilfiger", "Sebago"),
            listOf("knit", "cardigan", "quarterzip", "polo", "shirt", "chinos", "loafers", "blazer"), listOf("cashmere", "cable", "wool"),
            listOf("old money")),
        Style("quietluxury", "Quiet luxury", listOf("quiet lux", "stealth wealth", "luxe discret"),
            listOf("Loro Piana", "Brunello Cucinelli", "Max Mara", "The Row", "COS", "Massimo Dutti", "Arket", "Totême"),
            listOf("knit", "coat", "trousers", "loafers"), listOf("cashmere", "merino")),
        Style("preppy", "Preppy", listOf("bcbg", "college", "collegiate"),
            listOf("Ralph Lauren", "Tommy Hilfiger", "Lacoste", "Gant", "Abercrombie & Fitch", "Brooks Brothers", "Fred Perry"),
            listOf("polo", "rugby", "cardigan", "chinos", "shirt", "knit"), listOf("cable", "striped"), listOf("preppy")),
        Style("streetwear", "Streetwear", listOf("street wear", "street", "urban", "urbain", "hype", "hypebeast"),
            listOf("Nike", "Stüssy", "Carhartt", "Supreme", "The North Face", "Palace", "Corteiz", "Jordan", "Bape", "Off-White"),
            listOf("hoodie", "sweatshirt", "cargo", "sneakers", "tshirt", "puffer"), emptyList(), listOf("streetwear", "oversize")),
        Style("vintage", "Vintage", listOf("retro", "rétro", "90s", "années 90", "80s", "thrift", "friperie"),
            listOf("Levi's", "Ralph Lauren", "Nike", "Adidas", "Carhartt", "Champion", "Tommy Hilfiger"),
            listOf("jacket", "jeans", "sweatshirt", "windbreaker", "shirt"), emptyList(), listOf("vintage", "90s", "retro")),
        Style("y2k", "Y2K", listOf("an 2000", "années 2000", "2000s"),
            listOf("Ed Hardy", "Juicy Couture", "Miss Sixty", "Von Dutch", "Diesel", "True Religion", "Guess"),
            listOf("jeans", "top", "tracksuit", "bag"), emptyList(), listOf("y2k")),
        Style("gorpcore", "Gorpcore", listOf("outdoor", "rando", "hiking", "trail"),
            listOf("Arc'teryx", "The North Face", "Salomon", "Patagonia", "Columbia", "Helly Hansen"),
            listOf("fleece", "windbreaker", "puffer", "cargo", "sneakers"), emptyList(), listOf("gorpcore")),
        Style("techwear", "Techwear", listOf("tech wear", "tactical"),
            listOf("Nike", "Arc'teryx", "Stone Island", "CP Company", "Salomon"),
            listOf("cargo", "windbreaker", "jacket"), emptyList(), listOf("techwear")),
        Style("workwear", "Workwear", listOf("work wear", "vêtement de travail", "chore"),
            listOf("Carhartt", "Dickies", "Levi's", "Red Wing", "Lee", "Wrangler"),
            listOf("jacket", "overshirt", "cargo", "jeans", "boots"), listOf("denim"), listOf("workwear", "chore jacket")),
        Style("minimal", "Minimaliste", listOf("minimal", "minimalist", "minimaliste", "basique", "clean"),
            listOf("COS", "Arket", "Uniqlo", "A.P.C.", "Acne Studios", "Everlane", "Filippa K"),
            listOf("tshirt", "knit", "trousers", "coat", "shirt")),
        Style("skate", "Skate", listOf("skater", "skateboard"),
            listOf("Vans", "Dickies", "Thrasher", "Element", "Santa Cruz", "Palace", "HUF"),
            listOf("hoodie", "tshirt", "shorts", "jeans", "sneakers")),
        Style("blokecore", "Blokecore", listOf("bloke core", "football vintage", "terrace"),
            listOf("Adidas", "Umbro", "Kappa", "Nike", "Sergio Tacchini", "Fila"),
            listOf("jersey", "tracksuit", "sneakers", "windbreaker"), emptyList(), listOf("vintage", "retro")),
        Style("scandi", "Scandinave", listOf("scandi", "scandinavian", "copenhagen style"),
            listOf("Ganni", "Acne Studios", "Arket", "Samsøe Samsøe", "Filippa K", "Norse Projects", "Wood Wood"),
            listOf("knit", "dress", "coat", "trousers")),
        Style("boho", "Bohème", listOf("boho", "boheme", "bohemian", "hippie"),
            listOf("Free People", "Sézane", "Isabel Marant", "Rouje", "Ba&sh"),
            listOf("dress", "skirt", "top", "boots"), listOf("linen"), listOf("boho", "bohème")),
        Style("parisienne", "Parisienne", listOf("parisian", "french girl", "parisien"),
            listOf("Sézane", "Rouje", "Maje", "Sandro", "Ba&sh", "Claudie Pierlot", "Saint James"),
            listOf("dress", "cardigan", "top", "skirt", "jeans", "coat"), listOf("striped")),
        Style("athleisure", "Athleisure", listOf("sport chic", "gym", "sporty", "sportswear"),
            listOf("Lululemon", "Nike", "Alo Yoga", "Gymshark", "Adidas", "New Balance"),
            listOf("joggers", "hoodie", "sneakers", "top")),
        Style("archive", "Archive", listOf("designer", "avant garde", "avant-garde", "archive fashion"),
            listOf("Rick Owens", "Yohji Yamamoto", "Comme des Garçons", "Issey Miyake", "Maison Margiela", "Helmut Lang", "Raf Simons"),
            listOf("jacket", "trousers", "shirt", "boots"), emptyList(), listOf("archive")),
        Style("darkacademia", "Dark academia", listOf("dark academic", "academia"),
            listOf("Ralph Lauren", "Barbour", "Brooks Brothers", "Massimo Dutti"),
            listOf("blazer", "knit", "trousers", "coat", "loafers", "shirt"), listOf("tweed", "corduroy", "wool")),
        Style("japanese", "Japonais", listOf("japan", "japanese", "amekaji", "japonais"),
            listOf("Kapital", "Visvim", "Needles", "Uniqlo", "Evisu", "Yohji Yamamoto", "Issey Miyake"),
            listOf("jacket", "jeans", "shirt", "trousers")),
        Style("luxe", "Luxe", listOf("luxury", "luxe", "designer"),
            listOf("Gucci", "Prada", "Louis Vuitton", "Dior", "Saint Laurent", "Balenciaga", "Moncler", "Burberry"),
            listOf("bag", "sneakers", "jacket", "knit")),
        Style("mountain", "Ski / Montagne", listOf("ski", "montagne", "après ski"),
            listOf("Moncler", "Canada Goose", "The North Face", "Patagonia", "Arc'teryx", "Napapijri"),
            listOf("puffer", "fleece", "beanie", "bodywarmer")),
    )

    val phraseIndex: List<Pair<List<String>, Style>> = all.flatMap { s -> s.phrases.map { it to s } }.sortedByDescending { it.first.size }
    fun byId(id: String): Style? = all.firstOrNull { it.id == id }
}
