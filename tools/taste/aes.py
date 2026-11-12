# Contrastive aesthetic probes: (name, positive prompts, negative prompts). Score = P(positive).
PROBES = [
 ("big_logo", ["a sweatshirt with a huge printed brand logo across the chest", "clothing with large bold logo lettering printed on it", "a garment covered with a big graphic print"],
              ["a plain garment with a small embroidered logo", "a minimalist garment without any print", "a solid colour knit sweater"]),
 ("neon", ["a neon fluorescent bright garment", "a garment in garish flashy colours", "a highlighter yellow or neon green jacket"],
          ["a garment in muted neutral colours", "a garment in earthy tones like navy, cream, brown or black", "a garment in a deep rich colour"]),
 ("wrinkled", ["a wrinkled crumpled garment", "clothes thrown in a messy pile", "a creased garment lying on a bed"],
              ["a neatly laid out smooth garment", "an ironed garment on a hanger", "a carefully folded garment"]),
 ("damaged", ["a dirty stained garment", "a damaged worn out garment with holes and pilling", "a faded stretched old t-shirt"],
             ["a clean garment in excellent condition", "a like-new garment"]),
 ("badphoto", ["a dark blurry low quality photo of clothes", "a badly lit amateur photo of clothing"],
              ["a well lit sharp photo of clothing", "a clear bright product photo of a garment"]),
 ("noble", ["a garment made of rich textured wool, mohair or cashmere", "a chunky cable knit sweater", "a fluffy mohair knit", "a heavy rugged cotton canvas work jacket", "a quality leather jacket"],
           ["a garment made of thin shiny synthetic polyester", "a cheap thin cotton jersey garment", "a cheap acrylic garment"]),
 ("patina", ["a faded worn-in vintage workwear jacket with beautiful patina", "a vintage washed denim jacket"],
            ["a brand new stiff garment"]),
 ("stylish", ["a beautiful stylish desirable piece of clothing", "a timeless high quality garment", "a fashionable menswear piece", "a vintage designer garment"],
             ["an ugly cheap unfashionable piece of clothing", "a tacky garment", "a cheap fast fashion garment", "an outdated unattractive garment"]),
]
TEMPLATE = ["{}", "a photo of {}"]
