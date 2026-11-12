"""Text embeddings for the "Lens" analysis of reference photos: colours, materials / patterns and visible brand logos.
Ids are the engine's Lexicon / Brands ids. Adds the sections "colors", "materials", "brands" to taste_s2.json and
taste_s0.json (run from the folder holding the s2/ and s0/ model folders: python3 lens.py <assets dir> [<more json>])."""
import json, sys, numpy as np, clip

T = ["a photo of {}", "{}", "a close-up photo of {}"]
COLORS = {
    "black": ["a black garment", "black clothing"], "white": ["a white garment", "white clothing"],
    "cream": ["a cream off-white ecru garment", "an ivory cream sweater"], "grey": ["a grey garment", "heather grey clothing"],
    "navy": ["a navy blue garment", "dark navy clothing"], "blue": ["a royal blue garment", "a bright blue piece of clothing"],
    "lightblue": ["a light blue garment", "a sky blue or washed light denim garment"], "red": ["a red garment", "bright red clothing"],
    "burgundy": ["a burgundy wine red garment", "a maroon garment"], "green": ["a green garment", "forest green clothing"],
    "khaki": ["a khaki olive green garment", "an olive drab garment"], "beige": ["a beige tan garment", "a sand beige camel garment"],
    "brown": ["a brown garment", "a chocolate brown garment"], "pink": ["a pink garment", "pastel pink clothing"],
    "yellow": ["a yellow garment", "mustard yellow clothing"], "orange": ["an orange garment", "bright orange clothing"],
    "purple": ["a purple garment", "violet lilac clothing"],
}
MATERIALS = {
    "wool": ["a wool knit sweater", "a chunky wool knitwear piece", "a fuzzy mohair or wool knit"],
    "cotton": ["a cotton jersey garment", "a plain cotton t-shirt or sweatshirt"],
    "linen": ["a linen shirt", "a crumpled linen garment"],
    "leather": ["a leather jacket", "a smooth leather garment"],
    "suede": ["a suede jacket", "a suede garment"],
    "denim": ["a denim garment", "blue jeans denim fabric"],
    "corduroy": ["a corduroy garment", "ribbed corduroy fabric"],
    "tweed": ["a tweed jacket", "a wool tweed blazer"],
    "nylon": ["a nylon technical jacket", "a shiny nylon puffer or windbreaker"],
    "cable": ["a cable knit sweater", "an aran fisherman cable knit"],
    "striped": ["a striped garment", "a horizontal striped sweater"],
    "plaid": ["a plaid check garment", "a tartan flannel shirt"],
}
BRANDS = {
    "stussy": "Stüssy", "carhartt": "Carhartt", "ralphlauren": "Ralph Lauren", "lacoste": "Lacoste", "nike": "Nike",
    "adidas": "Adidas", "thenorthface": "The North Face", "supreme": "Supreme", "patagonia": "Patagonia",
    "arcteryx": "Arc'teryx", "stoneisland": "Stone Island", "levis": "Levi's", "tommyhilfiger": "Tommy Hilfiger",
    "champion": "Champion", "fredperry": "Fred Perry", "burberry": "Burberry", "moncler": "Moncler", "palace": "Palace",
    "corteiz": "Corteiz", "offwhite": "Off-White", "dickies": "Dickies", "newbalance": "New Balance", "barbour": "Barbour",
    "napapijri": "Napapijri", "puma": "Puma", "jordan": "Jordan", "vans": "Vans", "converse": "Converse", "gucci": "Gucci",
    "louisvuitton": "Louis Vuitton", "prada": "Prada", "balenciaga": "Balenciaga", "bape": "Bape",
}
BRAND_T = ["a {} logo", "a garment with the {} logo", "{} clothing"]
NONE = ["a plain garment with no visible logo", "an unbranded piece of clothing", "a garment without any brand logo"]

def mean(ps):
    e = clip.text_emb(ps).mean(0); return e / np.linalg.norm(e)
def r(v): return [round(float(x), 5) for x in v]

def sections():
    return {
        "colors": {k: r(mean([t.format(p) for p in ps for t in T])) for k, ps in COLORS.items()},
        "materials": {k: r(mean([t.format(p) for p in ps for t in T])) for k, ps in MATERIALS.items()},
        "brands": {**{k: r(mean([t.format(n) for t in BRAND_T])) for k, n in BRANDS.items()}, "none": r(mean(NONE))},
    }

if __name__ == "__main__":
    for D in ["s2", "s0"]:
        clip.D = D; clip._txt = None; clip.tok = clip.Tokenizer.from_file(f"{D}/tokenizer.json")
        sec = sections()
        for path in sys.argv[1:]:
            f = f"{path}/taste_{D}.json"
            try: d = json.load(open(f))
            except FileNotFoundError: continue
            d.update(sec); json.dump(d, open(f, "w"), separators=(",", ":")); print("updated", f)
