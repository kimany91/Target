package com.target.engine

/** Every language used by a Vinted marketplace, ordered by marketplace size (most listings first). */
enum class Lang(val code: String) {
    FR("fr"), DE("de"), IT("it"), ES("es"), NL("nl"), PL("pl"), EN("en"), PT("pt"),
    CS("cs"), LT("lt"), SK("sk"), RO("ro"), HU("hu"), HR("hr"), EL("el"), SV("sv"), FI("fi"), DA("da");

    companion object {
        fun fromCode(code: String): Lang? = entries.firstOrNull { it.code == code.lowercase() }
    }
}

/** A Vinted country site. Vinted shares one catalogue across the EU, so searching one domain in many languages
 *  already reaches listings from other countries; extra domains are optional. */
enum class VintedDomain(val host: String, val lang: Lang, val currency: String, val label: String) {
    FR("www.vinted.fr", Lang.FR, "EUR", "France"),
    BE("www.vinted.be", Lang.FR, "EUR", "Belgique"),
    LU("www.vinted.lu", Lang.FR, "EUR", "Luxembourg"),
    DE("www.vinted.de", Lang.DE, "EUR", "Deutschland"),
    AT("www.vinted.at", Lang.DE, "EUR", "Österreich"),
    IT("www.vinted.it", Lang.IT, "EUR", "Italia"),
    ES("www.vinted.es", Lang.ES, "EUR", "España"),
    NL("www.vinted.nl", Lang.NL, "EUR", "Nederland"),
    PL("www.vinted.pl", Lang.PL, "PLN", "Polska"),
    PT("www.vinted.pt", Lang.PT, "EUR", "Portugal"),
    UK("www.vinted.co.uk", Lang.EN, "GBP", "United Kingdom"),
    IE("www.vinted.ie", Lang.EN, "EUR", "Ireland"),
    CZ("www.vinted.cz", Lang.CS, "CZK", "Česko"),
    SK("www.vinted.sk", Lang.SK, "EUR", "Slovensko"),
    LT("www.vinted.lt", Lang.LT, "EUR", "Lietuva"),
    RO("www.vinted.ro", Lang.RO, "RON", "România"),
    HU("www.vinted.hu", Lang.HU, "HUF", "Magyarország"),
    HR("www.vinted.hr", Lang.HR, "EUR", "Hrvatska"),
    GR("www.vinted.gr", Lang.EL, "EUR", "Ελλάδα"),
    SE("www.vinted.se", Lang.SV, "SEK", "Sverige"),
    FI("www.vinted.fi", Lang.FI, "EUR", "Suomi"),
    DK("www.vinted.dk", Lang.DA, "DKK", "Danmark");

    /** Host of the catalogue API (moved from www.vinted.xx/api/v2 to api.vinted.xx/svc-catalogue in 2026). */
    val apiHost: String get() = apiHostFor(host)
    /** Value of the Locale / Accept-Language headers, e.g. "fr-FR". */
    val locale: String get() = "${lang.code}-${if (this == UK) "GB" else if (this == GR) "GR" else name}"

    companion object {
        fun apiHostFor(webHost: String): String = "api." + webHost.removePrefix("www.")
        fun fromHost(host: String): VintedDomain? = entries.firstOrNull { it.host == host || it.host.removePrefix("www.") == host }
        fun fromName(name: String?): VintedDomain = entries.firstOrNull { it.name == name } ?: FR
    }
}
