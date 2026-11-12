package com.target.engine

import com.target.engine.Lang.*

enum class ConceptKind { ITEM, MATERIAL, PATTERN, COLOR }

/** Price group used to estimate the market value of a listing (multiplier applied to a brand's base value). */
enum class PriceGroup(val multiplier: Double) {
    TEE(0.55), TOP(0.7), POLO(0.8), SHIRT(0.85), SWEAT(1.0), KNIT(1.15), DENIM(1.0), PANTS(0.9), SHORTS(0.6),
    SKIRT(0.8), DRESS(1.0), JACKET(1.7), BLAZER(1.6), LEATHER(2.6), COAT(2.0), PUFFER(2.3), TRACKSUIT(1.4),
    SUIT(2.5), SHOES(1.6), BOOTS(1.9), BAG(2.0), ACCESSORY(0.55), JERSEY(1.0), DEFAULT(1.0)
}

class Concept(
    val id: String,
    val kind: ConceptKind,
    val group: PriceGroup?,
    val terms: Map<Lang, List<String>>,
    /** Display label (French UI). */
    val label: String,
    /** Only for colours: sRGB used to map a dominant image colour to a name. */
    val rgb: Int? = null,
) {
    /** Normalised token lists of every term in every language, used for title matching. */
    val matchers: List<List<String>> = terms.values.flatten().map { Text.tokens(it) }.filter { it.isNotEmpty() }.distinct()

    fun primary(lang: Lang): String? = terms[lang]?.firstOrNull()
    fun synonyms(lang: Lang): List<String> = terms[lang].orEmpty()

    fun matches(titleTokens: List<String>): Boolean = matchers.any { Text.containsPhrase(titleTokens, it) }

    /** Start positions where [phrase] occurs in [hay] (light plural folding). */
    internal fun occurrences(hay: List<String>, phrase: List<String>): List<Int> =
        if (phrase.size > hay.size) emptyList()
        else (0..hay.size - phrase.size).filter { i -> phrase.indices.all { Text.tokenEquals(hay[i + it], phrase[it]) } }
}

private fun parse(vararg pairs: Pair<Lang, String>): Map<Lang, List<String>> =
    pairs.associate { (l, s) -> l to s.split('|').map { it.trim() }.filter { it.isNotEmpty() } }

private fun item(id: String, label: String, group: PriceGroup, vararg t: Pair<Lang, String>) =
    Concept(id, ConceptKind.ITEM, group, parse(*t), label)

private fun material(id: String, label: String, vararg t: Pair<Lang, String>) =
    Concept(id, ConceptKind.MATERIAL, null, parse(*t), label)

private fun pattern(id: String, label: String, vararg t: Pair<Lang, String>) =
    Concept(id, ConceptKind.PATTERN, null, parse(*t), label)

private fun color(id: String, label: String, rgb: Int, vararg t: Pair<Lang, String>) =
    Concept(id, ConceptKind.COLOR, null, parse(*t), label, rgb)

/**
 * Multilingual fashion dictionary covering the 18 Vinted languages.
 * First term of each language = the term used to build queries; the others are synonyms (also searched, and
 * used to recognise titles).
 */
object Lexicon {

    val items: List<Concept> = listOf(
        item("knit", "Pull / Maille", PriceGroup.KNIT,
            FR to "pull|pull en maille|maille|tricot|pull tricot", EN to "sweater|jumper|knit|knitwear|knitted sweater|knit sweater",
            DE to "pullover|strickpullover|pulli|strick", IT to "maglione|maglia|maglioncino", ES to "jersey|jersey de punto|suéter|punto",
            NL to "trui|gebreide trui|pullover", PL to "sweter|sweterek", PT to "camisola|camisola de malha|malha|suéter",
            CS to "svetr|pletený svetr", LT to "megztinis", SK to "sveter", RO to "pulover|pulover tricotat", HU to "pulóver|kötött pulóver",
            HR to "džemper|pleteni džemper", EL to "πουλόβερ", SV to "tröja|stickad tröja|stickat", FI to "neule|villapaita", DA to "striktrøje|strik|sweater"),
        item("cardigan", "Cardigan / Gilet", PriceGroup.KNIT,
            FR to "gilet|cardigan|gilet en maille", EN to "cardigan", DE to "strickjacke|cardigan", IT to "cardigan|maglia aperta",
            ES to "chaqueta de punto|cárdigan|rebeca", NL to "vest|cardigan", PL to "kardigan|sweter rozpinany", PT to "casaco de malha|cardigan",
            CS to "kardigan", LT to "kardiganas", SK to "kardigan", RO to "cardigan", HU to "kardigán", HR to "kardigan",
            EL to "ζακέτα", SV to "kofta|cardigan", FI to "neuletakki", DA to "cardigan"),
        item("quarterzip", "Pull col zippé", PriceGroup.KNIT,
            FR to "pull col zippé|demi zip|quarter zip|camionneur", EN to "quarter zip|half zip|1/4 zip", DE to "troyer|halfzip|half zip",
            IT to "mezza zip|maglione con zip", ES to "media cremallera|jersey con cremallera", NL to "halfzip|trui met rits",
            PL to "sweter z zamkiem", PT to "meio fecho|camisola com fecho", SV to "halfzip", DA to "halfzip"),
        item("hoodie", "Hoodie", PriceGroup.SWEAT,
            FR to "sweat à capuche|hoodie|pull à capuche|sweat capuche|veste à capuche", EN to "hoodie|hooded sweatshirt|hooded jumper|zip hoodie", DE to "hoodie|kapuzenpullover|kapuzenpulli|kapuzenjacke", IT to "felpa con cappuccio|hoodie|maglia con cappuccio",
            ES to "sudadera con capucha|jersey con capucha", NL to "hoodie|capuchon trui", PL to "bluza z kapturem", PT to "sweatshirt com capuz|hoodie",
            CS to "mikina s kapucí", LT to "džemperis su gobtuvu", SK to "mikina s kapucňou", RO to "hanorac", HU to "kapucnis pulóver",
            HR to "majica s kapuljačom", EL to "φούτερ με κουκούλα", SV to "huvtröja|hoodie", FI to "huppari", DA to "hættetrøje|hoodie"),
        item("sweatshirt", "Sweat", PriceGroup.SWEAT,
            FR to "sweat|sweatshirt|sweat col rond", EN to "sweatshirt|crewneck", DE to "sweatshirt|sweater", IT to "felpa",
            ES to "sudadera", NL to "sweater|sweatshirt", PL to "bluza", PT to "sweatshirt|camisola de algodão", CS to "mikina",
            LT to "džemperis", SK to "mikina", RO to "bluză de trening", HU to "melegítő felső", HR to "dukserica", EL to "φούτερ",
            SV to "sweatshirt|collegetröja", FI to "collegepaita", DA to "sweatshirt"),
        item("tshirt", "T-shirt", PriceGroup.TEE,
            FR to "t-shirt|tee-shirt|tee shirt", EN to "t-shirt|tee", DE to "t-shirt", IT to "maglietta|t-shirt", ES to "camiseta",
            NL to "t-shirt", PL to "koszulka|t-shirt", PT to "t-shirt|camiseta", CS to "tričko", LT to "marškinėliai", SK to "tričko",
            RO to "tricou", HU to "rövid ujjú felső", HR to "majica kratkih rukava", EL to "μπλουζάκι", SV to "t-shirt", FI to "t-paita", DA to "t-shirt"),
        item("shirt", "Chemise", PriceGroup.SHIRT,
            FR to "chemise|chemise oxford", EN to "shirt|button down|oxford shirt", DE to "hemd|oxfordhemd", IT to "camicia",
            ES to "camisa", NL to "overhemd|hemd", PL to "koszula", PT to "camisa", CS to "košile", LT to "marškiniai", SK to "košeľa",
            RO to "cămașă", HU to "ing", HR to "košulja", EL to "πουκάμισο", SV to "skjorta", FI to "kauluspaita", DA to "skjorte"),
        item("polo", "Polo", PriceGroup.POLO,
            FR to "polo", EN to "polo shirt|polo", DE to "poloshirt|polo", IT to "polo", ES to "polo", NL to "polo", PL to "koszulka polo|polo",
            PT to "polo", CS to "polokošile", LT to "polo marškinėliai", SK to "polokošeľa", RO to "tricou polo", HU to "galléros póló",
            HR to "polo majica", EL to "πόλο", SV to "pikétröja", FI to "pikeepaita", DA to "polo"),
        item("rugby", "Polo rugby", PriceGroup.POLO,
            FR to "polo rugby|maillot rugby", EN to "rugby shirt|rugby polo", DE to "rugbyshirt", IT to "polo rugby", ES to "polo rugby",
            NL to "rugbyshirt", PL to "koszulka rugby", PT to "polo rugby"),
        item("top", "Top / Blouse", PriceGroup.TOP,
            FR to "top|blouse|débardeur", EN to "top|blouse|tank top", DE to "top|bluse", IT to "top|camicetta|canotta", ES to "top|blusa",
            NL to "top|blouse", PL to "bluzka|top", PT to "blusa|top", CS to "halenka|top", LT to "palaidinė", SK to "blúzka",
            RO to "bluză|top", HU to "blúz|top", HR to "bluza|top", EL to "μπλούζα", SV to "blus|linne", FI to "pusero|toppi", DA to "bluse|top"),
        item("jacket", "Veste", PriceGroup.JACKET,
            FR to "veste|blouson", EN to "jacket", DE to "jacke", IT to "giacca|giubbotto", ES to "chaqueta|cazadora", NL to "jas|jack",
            PL to "kurtka", PT to "casaco|blusão", CS to "bunda", LT to "striukė", SK to "bunda", RO to "geacă", HU to "dzseki",
            HR to "jakna", EL to "μπουφάν", SV to "jacka", FI to "takki", DA to "jakke"),
        item("coat", "Manteau", PriceGroup.COAT,
            FR to "manteau|trench", EN to "coat|overcoat|trench coat", DE to "mantel|trenchcoat", IT to "cappotto|trench", ES to "abrigo|gabardina",
            NL to "mantel|trenchcoat", PL to "płaszcz", PT to "sobretudo|gabardine", CS to "kabát", LT to "paltas", SK to "kabát",
            RO to "palton", HU to "kabát", HR to "kaput", EL to "παλτό", SV to "kappa", FI to "päällystakki", DA to "frakke"),
        item("puffer", "Doudoune", PriceGroup.PUFFER,
            FR to "doudoune", EN to "puffer jacket|down jacket|puffer", DE to "daunenjacke|steppjacke|pufferjacke", IT to "piumino",
            ES to "plumífero|abrigo de plumas", NL to "pufferjas|donsjas", PL to "kurtka puchowa", PT to "casaco de penas|kispo",
            CS to "péřová bunda", LT to "pūkinė striukė", SK to "páperová bunda", RO to "geacă de puf", HU to "pehelykabát",
            HR to "pernata jakna", EL to "φουσκωτό μπουφάν", SV to "dunjacka", FI to "untuvatakki", DA to "dunjakke"),
        item("bodywarmer", "Doudoune sans manches", PriceGroup.JACKET,
            FR to "doudoune sans manches|gilet sans manches", EN to "gilet|bodywarmer|puffer vest", DE to "weste|daunenweste",
            IT to "smanicato|gilet imbottito", ES to "chaleco", NL to "bodywarmer", PL to "bezrękawnik", PT to "colete", SV to "väst", DA to "vest"),
        item("blazer", "Blazer", PriceGroup.BLAZER,
            FR to "blazer|veste de costume", EN to "blazer|sport coat", DE to "sakko|blazer", IT to "blazer|giacca elegante",
            ES to "americana|blazer", NL to "colbert|blazer", PL to "marynarka", PT to "blazer", CS to "sako", LT to "švarkas", SK to "sako",
            RO to "sacou", HU to "zakó", HR to "sako", EL to "σακάκι", SV to "kavaj", FI to "bleiseri", DA to "blazer"),
        item("leatherjacket", "Veste en cuir", PriceGroup.LEATHER,
            FR to "veste en cuir|perfecto|blouson cuir", EN to "leather jacket", DE to "lederjacke", IT to "giacca di pelle|giubbotto in pelle",
            ES to "chaqueta de cuero|cazadora de cuero", NL to "leren jas", PL to "kurtka skórzana", PT to "casaco de pele|blusão de couro",
            CS to "kožená bunda", LT to "odinė striukė", SK to "kožená bunda", RO to "geacă de piele", HU to "bőrdzseki", HR to "kožna jakna",
            EL to "δερμάτινο μπουφάν", SV to "skinnjacka", FI to "nahkatakki", DA to "læderjakke"),
        item("bomber", "Bomber", PriceGroup.JACKET,
            FR to "bomber|blouson aviateur", EN to "bomber jacket|bomber", DE to "bomberjacke", IT to "bomber", ES to "bomber|cazadora bomber",
            NL to "bomberjack", PL to "kurtka bomber", PT to "bomber", SV to "bomberjacka", DA to "bomberjakke"),
        item("windbreaker", "Coupe-vent", PriceGroup.JACKET,
            FR to "coupe-vent|k-way|windbreaker", EN to "windbreaker|track jacket|shell jacket", DE to "windbreaker|windjacke|regenjacke",
            IT to "giacca a vento|k-way", ES to "cortavientos", NL to "windjack|regenjas", PL to "wiatrówka", PT to "corta-vento",
            CS to "větrovka", SK to "vetrovka", SV to "vindjacka", FI to "tuulitakki", DA to "vindjakke"),
        item("fleece", "Polaire", PriceGroup.SWEAT,
            FR to "polaire|veste polaire", EN to "fleece|fleece jacket", DE to "fleecejacke|fleece", IT to "pile", ES to "forro polar",
            NL to "fleece|fleecevest", PL to "polar", PT to "polar|casaco polar", CS to "flísová mikina", SV to "fleecetröja", FI to "fleece", DA to "fleece"),
        item("overshirt", "Surchemise", PriceGroup.SHIRT,
            FR to "surchemise|chemise épaisse", EN to "overshirt|shacket", DE to "overshirt|hemdjacke", IT to "sovracamicia", ES to "sobrecamisa",
            NL to "overshirt", PL to "koszula wierzchnia", PT to "sobrecamisa"),
        item("jeans", "Jean", PriceGroup.DENIM,
            FR to "jean|jeans", EN to "jeans|denim jeans", DE to "jeans", IT to "jeans", ES to "vaqueros|jeans", NL to "spijkerbroek|jeans",
            PL to "jeansy|dżinsy", PT to "calças de ganga|jeans", CS to "džíny", LT to "džinsai", SK to "džínsy", RO to "blugi",
            HU to "farmer", HR to "traperice", EL to "τζιν", SV to "jeans", FI to "farkut", DA to "jeans"),
        item("trousers", "Pantalon", PriceGroup.PANTS,
            FR to "pantalon", EN to "trousers|pants", DE to "hose", IT to "pantaloni", ES to "pantalón|pantalones", NL to "broek",
            PL to "spodnie", PT to "calças", CS to "kalhoty", LT to "kelnės", SK to "nohavice", RO to "pantaloni", HU to "nadrág",
            HR to "hlače", EL to "παντελόνι", SV to "byxor", FI to "housut", DA to "bukser"),
        item("chinos", "Chino", PriceGroup.PANTS,
            FR to "chino|pantalon chino", EN to "chinos|chino trousers", DE to "chinohose|chino", IT to "chino|pantaloni chino",
            ES to "chinos|pantalón chino", NL to "chino", PL to "chinosy", PT to "chinos", CS to "chino kalhoty", SV to "chinos", DA to "chinos"),
        item("cargo", "Cargo", PriceGroup.PANTS,
            FR to "pantalon cargo|cargo", EN to "cargo pants|cargo trousers|cargos", DE to "cargohose", IT to "pantaloni cargo",
            ES to "pantalón cargo", NL to "cargobroek", PL to "bojówki|spodnie cargo", PT to "calças cargo", CS to "cargo kalhoty",
            SK to "cargo nohavice", RO to "pantaloni cargo", HU to "cargo nadrág", SV to "cargobyxor", FI to "cargohousut", DA to "cargobukser"),
        item("joggers", "Jogging", PriceGroup.PANTS,
            FR to "jogging|pantalon de survêtement", EN to "joggers|track pants|sweatpants", DE to "jogginghose", IT to "pantaloni tuta",
            ES to "pantalón de chándal", NL to "joggingbroek", PL to "spodnie dresowe", PT to "calças de fato de treino", CS to "tepláky",
            SK to "tepláky", RO to "pantaloni de trening", HU to "melegítőnadrág", SV to "mjukisbyxor", DA to "joggingbukser"),
        item("tracksuit", "Survêtement", PriceGroup.TRACKSUIT,
            FR to "survêtement|ensemble survêtement", EN to "tracksuit", DE to "trainingsanzug", IT to "tuta", ES to "chándal",
            NL to "trainingspak", PL to "dres", PT to "fato de treino", CS to "tepláková souprava", RO to "trening", HU to "melegítő",
            HR to "trenirka", SV to "träningsoverall", DA to "træningsdragt"),
        item("shorts", "Short", PriceGroup.SHORTS,
            FR to "short|bermuda", EN to "shorts", DE to "shorts|kurze hose", IT to "pantaloncini|bermuda", ES to "pantalón corto|bermudas",
            NL to "korte broek", PL to "spodenki|szorty", PT to "calções", CS to "kraťasy", LT to "šortai", SK to "šortky", RO to "pantaloni scurți",
            HU to "rövidnadrág", HR to "kratke hlače", SV to "shorts", FI to "shortsit", DA to "shorts"),
        item("skirt", "Jupe", PriceGroup.SKIRT,
            FR to "jupe", EN to "skirt", DE to "rock", IT to "gonna", ES to "falda", NL to "rok", PL to "spódnica", PT to "saia",
            CS to "sukně", LT to "sijonas", SK to "sukňa", RO to "fustă", HU to "szoknya", HR to "suknja", EL to "φούστα", SV to "kjol", FI to "hame", DA to "nederdel"),
        item("dress", "Robe", PriceGroup.DRESS,
            FR to "robe", EN to "dress", DE to "kleid", IT to "vestito|abito", ES to "vestido", NL to "jurk", PL to "sukienka", PT to "vestido",
            CS to "šaty", LT to "suknelė", SK to "šaty", RO to "rochie", HU to "ruha", HR to "haljina", EL to "φόρεμα", SV to "klänning", FI to "mekko", DA to "kjole"),
        item("suit", "Costume", PriceGroup.SUIT,
            FR to "costume|costume homme", EN to "suit", DE to "anzug", IT to "completo|abito uomo", ES to "traje", NL to "kostuum|pak",
            PL to "garnitur", PT to "fato", CS to "oblek", LT to "kostiumas", SK to "oblek", RO to "costum", HU to "öltöny", HR to "odijelo",
            SV to "kostym", FI to "puku", DA to "jakkesæt"),
        item("jersey", "Maillot de foot", PriceGroup.JERSEY,
            FR to "maillot de foot|maillot football", EN to "football shirt|football jersey|soccer jersey", DE to "trikot|fußballtrikot",
            IT to "maglia calcio", ES to "camiseta de fútbol", NL to "voetbalshirt", PL to "koszulka piłkarska", PT to "camisola de futebol",
            CS to "fotbalový dres", RO to "tricou de fotbal", HR to "nogometni dres", SV to "fotbollströja", DA to "fodboldtrøje"),
        item("sneakers", "Sneakers", PriceGroup.SHOES,
            FR to "baskets|sneakers", EN to "sneakers|trainers", DE to "sneaker|turnschuhe", IT to "sneakers|scarpe da ginnastica",
            ES to "zapatillas", NL to "sneakers", PL to "sneakersy|buty sportowe", PT to "sapatilhas|ténis", CS to "tenisky",
            LT to "sportbačiai", SK to "tenisky", RO to "adidași|sneakers", HU to "sportcipő", HR to "tenisice", EL to "αθλητικά παπούτσια",
            SV to "sneakers", FI to "lenkkarit", DA to "sneakers"),
        item("boots", "Bottes / Boots", PriceGroup.BOOTS,
            FR to "bottes|boots|bottines", EN to "boots", DE to "stiefel|boots", IT to "stivali", ES to "botas", NL to "laarzen",
            PL to "botki|kozaki", PT to "botas", CS to "boty kotníkové|kozačky", LT to "batai aulinukai", SK to "čižmy", RO to "cizme",
            HU to "csizma", HR to "čizme", EL to "μπότες", SV to "kängor|stövlar", FI to "saappaat", DA to "støvler"),
        item("loafers", "Mocassins", PriceGroup.SHOES,
            FR to "mocassins|loafers", EN to "loafers", DE to "loafer|slipper", IT to "mocassini", ES to "mocasines", NL to "loafers",
            PL to "mokasyny", PT to "mocassins", CS to "mokasíny", SK to "mokasíny", RO to "mocasini", HU to "mokaszin", HR to "mokasinke",
            SV to "loafers", DA to "loafers"),
        item("shoes", "Chaussures", PriceGroup.SHOES,
            FR to "chaussures", EN to "shoes", DE to "schuhe", IT to "scarpe", ES to "zapatos", NL to "schoenen", PL to "buty",
            PT to "sapatos", CS to "boty", LT to "batai", SK to "topánky", RO to "pantofi", HU to "cipő", HR to "cipele", EL to "παπούτσια",
            SV to "skor", FI to "kengät", DA to "sko"),
        item("bag", "Sac", PriceGroup.BAG,
            FR to "sac|sac à main", EN to "bag|handbag", DE to "tasche|handtasche", IT to "borsa", ES to "bolso", NL to "tas|handtas",
            PL to "torebka|torba", PT to "mala|bolsa", CS to "kabelka", LT to "rankinė", SK to "kabelka", RO to "geantă", HU to "táska",
            HR to "torba", EL to "τσάντα", SV to "väska", FI to "laukku", DA to "taske"),
        item("cap", "Casquette", PriceGroup.ACCESSORY,
            FR to "casquette", EN to "cap|baseball cap", DE to "kappe|cap", IT to "cappellino", ES to "gorra", NL to "pet cap",
            PL to "czapka z daszkiem", PT to "boné", CS to "kšiltovka", LT to "kepurė su snapeliu", SK to "šiltovka", RO to "șapcă",
            HU to "baseball sapka", HR to "šilterica", EL to "καπέλο", SV to "keps", FI to "lippis", DA to "kasket"),
        item("beanie", "Bonnet", PriceGroup.ACCESSORY,
            FR to "bonnet", EN to "beanie", DE to "mütze|beanie", IT to "berretto", ES to "gorro", NL to "muts", PL to "czapka",
            PT to "gorro", CS to "čepice", LT to "kepurė", SK to "čiapka", RO to "căciulă", HU to "sapka", HR to "kapa", EL to "σκούφος",
            SV to "mössa", FI to "pipo", DA to "hue"),
        item("scarf", "Écharpe", PriceGroup.ACCESSORY,
            FR to "écharpe|foulard|chèche", EN to "scarf|bandana", DE to "schal|halstuch", IT to "sciarpa", ES to "bufanda", NL to "sjaal", PL to "szalik", PT to "cachecol",
            CS to "šála", LT to "šalikas", SK to "šál", RO to "fular", HU to "sál", HR to "šal", EL to "κασκόλ", SV to "halsduk", FI to "kaulahuivi", DA to "tørklæde"),
        item("belt", "Ceinture", PriceGroup.ACCESSORY,
            FR to "ceinture", EN to "belt", DE to "gürtel", IT to "cintura", ES to "cinturón", NL to "riem", PL to "pasek", PT to "cinto",
            CS to "pásek", LT to "diržas", SK to "opasok", RO to "curea", HU to "öv", HR to "remen", EL to "ζώνη", SV to "bälte", FI to "vyö", DA to "bælte"),
        item("sunglasses", "Lunettes de soleil", PriceGroup.ACCESSORY,
            FR to "lunettes de soleil", EN to "sunglasses", DE to "sonnenbrille", IT to "occhiali da sole", ES to "gafas de sol",
            NL to "zonnebril", PL to "okulary przeciwsłoneczne", PT to "óculos de sol", SV to "solglasögon", DA to "solbriller"),
        item("watch", "Montre", PriceGroup.BAG,
            FR to "montre", EN to "watch", DE to "uhr|armbanduhr", IT to "orologio", ES to "reloj", NL to "horloge", PL to "zegarek",
            PT to "relógio", CS to "hodinky", SV to "klocka", DA to "armbåndsur"),
    )

    val materials: List<Concept> = listOf(
        material("cashmere", "Cachemire", FR to "cachemire", EN to "cashmere", DE to "kaschmir", IT to "cashmere|cachemire", ES to "cachemira",
            NL to "kasjmier", PL to "kaszmir", PT to "caxemira", CS to "kašmír", LT to "kašmyras", SK to "kašmír", RO to "cașmir",
            HU to "kasmír", HR to "kašmir", EL to "κασμίρ", SV to "kashmir", FI to "kashmir", DA to "kashmir"),
        material("merino", "Mérinos", FR to "mérinos|laine mérinos", EN to "merino|merino wool", DE to "merino|merinowolle", IT to "merino|lana merino",
            ES to "merino|lana merino", NL to "merino", PL to "merino", PT to "merino", SV to "merino", DA to "merino"),
        material("wool", "Laine", FR to "laine", EN to "wool|lambswool", DE to "wolle", IT to "lana", ES to "lana", NL to "wol", PL to "wełna|wełniany",
            PT to "lã", CS to "vlna", LT to "vilna", SK to "vlna", RO to "lână", HU to "gyapjú", HR to "vuna", EL to "μαλλί", SV to "ull", FI to "villa", DA to "uld"),
        material("cotton", "Coton", FR to "coton", EN to "cotton", DE to "baumwolle", IT to "cotone", ES to "algodón", NL to "katoen", PL to "bawełna",
            PT to "algodão", CS to "bavlna", LT to "medvilnė", SK to "bavlna", RO to "bumbac", HU to "pamut", HR to "pamuk", EL to "βαμβάκι",
            SV to "bomull", FI to "puuvilla", DA to "bomuld"),
        material("linen", "Lin", FR to "lin", EN to "linen", DE to "leinen", IT to "lino", ES to "lino", NL to "linnen", PL to "lniany", PT to "linho",
            CS to "lněný", LT to "lininis", SK to "ľanový", EL to "λινό", SV to "linne", FI to "pellava", DA to "hør"),
        material("leather", "Cuir", FR to "cuir", EN to "leather", DE to "leder", IT to "pelle", ES to "cuero|piel", NL to "leer|leren", PL to "skóra|skórzana",
            PT to "couro|pele", CS to "kůže|kožená", LT to "oda|odinė", SK to "koža|kožená", RO to "piele", HU to "bőr", HR to "koža", EL to "δέρμα",
            SV to "läder|skinn", FI to "nahka", DA to "læder"),
        material("suede", "Daim", FR to "daim|suède", EN to "suede", DE to "wildleder", IT to "camoscio", ES to "ante", NL to "suède", PL to "zamsz",
            PT to "camurça", CS to "semiš", LT to "zomša", SK to "semiš", RO to "piele întoarsă", HU to "velúr", HR to "brušena koža", SV to "mocka", FI to "mokka", DA to "ruskind"),
        material("denim", "Denim", FR to "denim|jean", EN to "denim", DE to "denim|jeans", IT to "denim|jeans", ES to "denim|vaquero", NL to "denim",
            PL to "jeans|denim", PT to "ganga|denim", SV to "denim", DA to "denim"),
        material("silk", "Soie", FR to "soie", EN to "silk", DE to "seide", IT to "seta", ES to "seda", NL to "zijde", PL to "jedwab", PT to "seda",
            CS to "hedvábí", LT to "šilkas", SK to "hodváb", RO to "mătase", HU to "selyem", HR to "svila", EL to "μετάξι", SV to "siden", FI to "silkki", DA to "silke"),
        material("corduroy", "Velours côtelé", FR to "velours côtelé|velours", EN to "corduroy|cord", DE to "cord|cordhose", IT to "velluto a coste|velluto",
            ES to "pana", NL to "ribfluweel|corduroy", PL to "sztruks", PT to "bombazine", CS to "manšestr", LT to "velvetas", SK to "menčester",
            RO to "reiat", HU to "kordbársony", SV to "manchester", FI to "vakosametti", DA to "fløjl"),
        material("tweed", "Tweed", FR to "tweed", EN to "tweed", DE to "tweed", IT to "tweed", ES to "tweed", NL to "tweed", PL to "tweed", PT to "tweed"),
    )

    val patterns: List<Concept> = listOf(
        pattern("cable", "Torsadé", FR to "torsadé|torsades", EN to "cable knit", DE to "zopfmuster", IT to "a trecce", ES to "ochos|trenzado",
            NL to "kabeltrui", PL to "warkocz", PT to "tranças", SV to "flätstickad", DA to "snoninger"),
        pattern("striped", "Rayé", FR to "rayé|rayures|marinière", EN to "striped|stripes", DE to "gestreift", IT to "a righe|righe", ES to "de rayas|rayas",
            NL to "gestreept", PL to "w paski", PT to "às riscas|riscas", SV to "randig", DA to "stribet"),
        pattern("plaid", "Carreaux", FR to "carreaux|tartan|écossais", EN to "plaid|check|tartan|flannel", DE to "kariert|karo", IT to "a quadri|quadri|scozzese",
            ES to "de cuadros|cuadros", NL to "geruit|ruit", PL to "w kratę|krata", PT to "xadrez", SV to "rutig", DA to "ternet"),
        pattern("logo", "Logo brodé", FR to "logo brodé", EN to "embroidered logo", DE to "logo bestickt", IT to "logo ricamato", ES to "logo bordado"),
        pattern("vintage", "Vintage", FR to "vintage|rétro", EN to "vintage|retro", DE to "vintage|retro", IT to "vintage", ES to "vintage", NL to "vintage",
            PL to "vintage", PT to "vintage"),
    )

    val colors: List<Concept> = listOf(
        color("black", "Noir", 0x111111, FR to "noir", EN to "black", DE to "schwarz", IT to "nero", ES to "negro", NL to "zwart", PL to "czarny",
            PT to "preto", CS to "černý", LT to "juodas", SK to "čierny", RO to "negru", HU to "fekete", HR to "crni", EL to "μαύρο", SV to "svart", FI to "musta", DA to "sort"),
        color("white", "Blanc", 0xF4F4F2, FR to "blanc", EN to "white", DE to "weiß", IT to "bianco", ES to "blanco", NL to "wit", PL to "biały",
            PT to "branco", CS to "bílý", LT to "baltas", SK to "biely", RO to "alb", HU to "fehér", HR to "bijeli", EL to "λευκό", SV to "vit", FI to "valkoinen", DA to "hvid"),
        color("cream", "Crème / Écru", 0xEDE3CF, FR to "écru|crème|blanc cassé", EN to "cream|ecru|off white", DE to "creme|ecru", IT to "panna|crema",
            ES to "crudo|crema", NL to "crème|ecru", PL to "kremowy|ecru", PT to "creme|cru"),
        color("grey", "Gris", 0x8E8E8E, FR to "gris", EN to "grey|gray", DE to "grau", IT to "grigio", ES to "gris", NL to "grijs", PL to "szary",
            PT to "cinzento|cinza", CS to "šedý", LT to "pilkas", SK to "sivý", RO to "gri", HU to "szürke", HR to "sivi", EL to "γκρι", SV to "grå", FI to "harmaa", DA to "grå"),
        color("navy", "Bleu marine", 0x1C2541, FR to "bleu marine|marine", EN to "navy|navy blue", DE to "marineblau|navy|dunkelblau", IT to "blu navy|blu scuro",
            ES to "azul marino", NL to "marineblauw|donkerblauw", PL to "granatowy", PT to "azul marinho", CS to "tmavě modrý", SV to "marinblå", DA to "navy"),
        color("blue", "Bleu", 0x3A6FD8, FR to "bleu", EN to "blue", DE to "blau", IT to "blu|azzurro", ES to "azul", NL to "blauw", PL to "niebieski",
            PT to "azul", CS to "modrý", LT to "mėlynas", SK to "modrý", RO to "albastru", HU to "kék", HR to "plavi", EL to "μπλε", SV to "blå", FI to "sininen", DA to "blå"),
        color("lightblue", "Bleu ciel", 0x9CC7EE, FR to "bleu ciel|bleu clair", EN to "light blue|sky blue|baby blue", DE to "hellblau", IT to "celeste|azzurro",
            ES to "azul claro|celeste", NL to "lichtblauw", PL to "błękitny", PT to "azul claro"),
        color("red", "Rouge", 0xC62828, FR to "rouge", EN to "red", DE to "rot", IT to "rosso", ES to "rojo", NL to "rood", PL to "czerwony", PT to "vermelho",
            CS to "červený", LT to "raudonas", SK to "červený", RO to "roșu", HU to "piros", HR to "crveni", EL to "κόκκινο", SV to "röd", FI to "punainen", DA to "rød"),
        color("burgundy", "Bordeaux", 0x6D1A2B, FR to "bordeaux", EN to "burgundy|maroon", DE to "bordeaux|weinrot", IT to "bordeaux|bordò", ES to "burdeos|granate",
            NL to "bordeaux", PL to "bordowy", PT to "bordeaux|grená"),
        color("green", "Vert", 0x2E7D32, FR to "vert", EN to "green", DE to "grün", IT to "verde", ES to "verde", NL to "groen", PL to "zielony", PT to "verde",
            CS to "zelený", LT to "žalias", SK to "zelený", RO to "verde", HU to "zöld", HR to "zeleni", EL to "πράσινο", SV to "grön", FI to "vihreä", DA to "grøn"),
        color("khaki", "Kaki", 0x6B6B3A, FR to "kaki|vert olive", EN to "khaki|olive", DE to "khaki|oliv", IT to "kaki|verde oliva", ES to "caqui|verde oliva",
            NL to "kaki|olijfgroen", PL to "khaki|oliwkowy", PT to "caqui"),
        color("beige", "Beige", 0xD2B48C, FR to "beige|camel|sable", EN to "beige|camel|tan|sand", DE to "beige|camel", IT to "beige|cammello", ES to "beige|camel",
            NL to "beige|camel", PL to "beżowy", PT to "bege", CS to "béžový", RO to "bej", HU to "bézs", SV to "beige", DA to "beige"),
        color("brown", "Marron", 0x6D4C33, FR to "marron|chocolat", EN to "brown|chocolate", DE to "braun", IT to "marrone", ES to "marrón", NL to "bruin",
            PL to "brązowy", PT to "castanho|marrom", CS to "hnědý", LT to "rudas", SK to "hnedý", RO to "maro", HU to "barna", HR to "smeđi", SV to "brun", FI to "ruskea", DA to "brun"),
        color("pink", "Rose", 0xEC8FB0, FR to "rose", EN to "pink", DE to "rosa", IT to "rosa", ES to "rosa", NL to "roze", PL to "różowy", PT to "rosa",
            CS to "růžový", SK to "ružový", RO to "roz", HU to "rózsaszín", SV to "rosa", DA to "lyserød"),
        color("yellow", "Jaune", 0xF2C94C, FR to "jaune", EN to "yellow", DE to "gelb", IT to "giallo", ES to "amarillo", NL to "geel", PL to "żółty",
            PT to "amarelo", CS to "žlutý", RO to "galben", HU to "sárga", SV to "gul", DA to "gul"),
        color("orange", "Orange", 0xEF7D2D, FR to "orange", EN to "orange", DE to "orange", IT to "arancione", ES to "naranja", NL to "oranje",
            PL to "pomarańczowy", PT to "laranja", RO to "portocaliu", SV to "orange", DA to "orange"),
        color("purple", "Violet", 0x7B4FA0, FR to "violet|mauve", EN to "purple|lilac", DE to "lila|violett", IT to "viola", ES to "morado|lila", NL to "paars",
            PL to "fioletowy", PT to "roxo", RO to "mov", SV to "lila", DA to "lilla"),
    )

    val all: List<Concept> = items + materials + patterns + colors
    private val byId = all.associateBy { it.id }
    fun concept(id: String): Concept? = byId[id]

    /** Every normalised phrase → concept, longest phrases first for greedy parsing. */
    val phraseIndex: List<Pair<List<String>, Concept>> =
        all.flatMap { c -> c.matchers.map { it to c } }.sortedByDescending { it.first.size }

    /** All single tokens that are part of the dictionary (used to forbid fuzzy brand matching on real words). */
    val knownWords: Set<String> by lazy { all.flatMap { c -> c.matchers.flatten() }.toSet() + COMMON_WORDS }

    /** Single words that also mean something else (fabric "jersey", "top quality"...): used only when nothing else matches. */
    private val WEAK_TERMS = setOf("jersey", "top", "vest", "sweater", "tuta", "polo", "pull")

    /**
     * Pieces named in a title, most specific first. A term inside a longer term of another piece is ignored
     * ("pull à capuche" is a hoodie, not a knit) and ambiguous words only count when nothing clearer is present
     * ("foulard jersey" is a scarf).
     */
    fun detectItems(tokens: List<String>): List<Concept> = itemEvidence(tokens).first

    /** Detected pieces + whether the evidence only comes from ambiguous words. */
    fun itemEvidence(tokens: List<String>): Pair<List<Concept>, Boolean> {
        class M(val c: Concept, val start: Int, val len: Int, val weak: Boolean)
        val ms = ArrayList<M>()
        for (c in items) for (p in c.matchers) for (i in c.occurrences(tokens, p)) ms += M(c, i, p.size, p.size == 1 && p[0] in WEAK_TERMS)
        if (ms.isEmpty()) return emptyList<Concept>() to false
        val kept = ms.filter { m -> ms.none { o -> o.c !== m.c && o.len > m.len && o.start <= m.start && o.start + o.len >= m.start + m.len } }
        val strong = kept.filter { !it.weak }
        val list = (strong.ifEmpty { kept }).sortedWith(compareByDescending<M> { it.len }.thenBy { it.start }).map { it.c }.distinct()
        return list to strong.isEmpty()
    }

    /** Map a free English label (e.g. from on-device image labelling) to concepts. */
    fun conceptsForLabel(label: String): List<Concept> {
        val tokens = Text.tokens(label)
        if (tokens.isEmpty()) return emptyList()
        val extra = LABEL_ALIASES[tokens.joinToString(" ")]
        if (extra != null) return extra.mapNotNull { concept(it) }
        return all.filter { it.kind != ConceptKind.COLOR && it.terms[EN].orEmpty().any { t -> Text.tokens(t) == tokens || Text.containsPhrase(tokens, Text.tokens(t)) } }
    }

    /** Nearest colour concept for an sRGB colour (CIE76 distance in Lab). */
    fun nearestColor(rgb: Int): Concept {
        val lab = Color.toLab(rgb)
        return colors.minBy { Color.deltaE(lab, Color.toLab(it.rgb!!)) }
    }

    private val LABEL_ALIASES = mapOf(
        "jersey" to listOf("knit"), "outerwear" to listOf("jacket"), "denim" to listOf("jeans"), "sleeve" to listOf(),
        "footwear" to listOf("shoes"), "sneaker" to listOf("sneakers"), "handbag" to listOf("bag"), "hat" to listOf("cap"),
        "woolen" to listOf("wool"), "sweater" to listOf("knit"), "jumper" to listOf("knit"), "fur" to listOf("coat"),
        "cardigan" to listOf("cardigan"), "hood" to listOf("hoodie"), "boot" to listOf("boots"), "skirt" to listOf("skirt"),
        "shorts" to listOf("shorts"), "jeans" to listOf("jeans"), "jacket" to listOf("jacket"), "coat" to listOf("coat"),
        "blazer" to listOf("blazer"), "suit" to listOf("suit"), "dress" to listOf("dress"), "shoe" to listOf("shoes"),
        "sunglasses" to listOf("sunglasses"), "watch" to listOf("watch"), "scarf" to listOf("scarf"), "leather" to listOf("leather"),
        "t shirt" to listOf("tshirt"), "polo" to listOf("polo"),
    )

    private val COMMON_WORDS = setOf(
        "homme", "femme", "enfant", "taille", "neuf", "nouveau", "nice", "tres", "bon", "etat", "avec", "sans", "pour", "the", "and",
        "new", "size", "men", "women", "mens", "womens", "herren", "damen", "uomo", "donna", "hombre", "mujer", "heren", "dames",
        "oversize", "oversized", "slim", "regular", "fit", "vintage", "retro", "style", "made", "true", "classic", "original",
        "noir", "blanc", "bleu", "rouge", "vert", "gris", "rose", "beige", "marron", "jaune",
    )
}

/** Small colour-science helper (sRGB → CIELab). */
object Color {
    fun toLab(rgb: Int): DoubleArray {
        fun lin(c: Int): Double { val v = c / 255.0; return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4) }
        val r = lin((rgb shr 16) and 0xFF); val g = lin((rgb shr 8) and 0xFF); val b = lin(rgb and 0xFF)
        val x = (r * 0.4124 + g * 0.3576 + b * 0.1805) / 0.95047
        val y = (r * 0.2126 + g * 0.7152 + b * 0.0722)
        val z = (r * 0.0193 + g * 0.1192 + b * 0.9505) / 1.08883
        fun f(t: Double) = if (t > 0.008856) Math.cbrt(t) else 7.787 * t + 16.0 / 116
        val fx = f(x); val fy = f(y); val fz = f(z)
        return doubleArrayOf(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    fun deltaE(a: DoubleArray, b: DoubleArray): Double {
        val dl = a[0] - b[0]; val da = a[1] - b[1]; val db = a[2] - b[2]
        return Math.sqrt(dl * dl + da * da + db * db)
    }
}
