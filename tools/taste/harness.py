"""Embeds Vinted thumbnails for LiveHarness and draws contact sheets of the results.
usage: python3 harness.py embed <dir>        (reads candidates.json, writes emb.json, caches thumbs in <dir>/thumbs)
       python3 harness.py sheet <dir> <tag> <n> (draws <dir>/<tag>.jpg from results.tsv rows starting with tag)"""
import io, json, os, sys, urllib.request, concurrent.futures as cf
import numpy as np
from PIL import Image, ImageDraw
import clip

def fetch(url, path):
    if os.path.exists(path): return path
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=20) as r: data = r.read()
    Image.open(io.BytesIO(data)).convert("RGB").save(path, "JPEG", quality=92)
    return path

def embed(d):
    cand = json.load(open(f"{d}/candidates.json")); os.makedirs(f"{d}/thumbs", exist_ok=True)
    with cf.ThreadPoolExecutor(12) as ex:
        paths = dict(zip(cand, ex.map(lambda kv: _safe(kv), cand.items())))
    ok = {k: p for k, p in paths.items() if p}
    clip.D = os.environ.get("CLIP_DIR", "s2"); clip._vis.clear()
    keys = list(ok); out = {}
    for i in range(0, len(keys), 16):
        E = clip.img_emb([Image.open(ok[k]) for k in keys[i:i + 16]], os.environ.get("CLIP_VARIANT", "vision_model_fp16"))
        out.update({k: [round(float(x), 5) for x in e] for k, e in zip(keys[i:i + 16], E)})
    json.dump(out, open(f"{d}/emb.json", "w")); print("embedded", len(out), "/", len(cand))

def _safe(kv):
    k, url = kv
    try: return fetch(url, f"{sys.argv[2]}/thumbs/{k}.jpg") if url else None
    except Exception: return None

def sheet(d, tag, n):
    rows = [l.rstrip("\n").split("\t") for l in open(f"{d}/results.tsv") if l.startswith(tag)][:n]
    W, H, cols = 200, 270, 6
    img = Image.new("RGB", (W * cols, (H + 46) * ((len(rows) + cols - 1) // cols)), "white"); dr = ImageDraw.Draw(img)
    for i, r in enumerate(rows):
        x, y = (i % cols) * W, (i // cols) * (H + 46)
        p = f"{d}/thumbs/{r[1]}.jpg"
        if os.path.exists(p): img.paste(Image.open(p).resize((W - 4, H)), (x + 2, y))
        dr.rectangle([x + 2, y + H, x + W - 2, y + H + 44], fill="black")
        dr.text((x + 6, y + H + 2), f"{r[0].replace('SHOWN','#')} b={r[2]} {r[4]}", fill="yellow")
        dr.text((x + 6, y + H + 16), f"{r[5][:14]} {r[7]}", fill="white"); dr.text((x + 6, y + H + 30), r[8][:30], fill="white")
    img.save(f"{d}/{tag}.jpg", quality=80); print("sheet", tag, len(rows))

if __name__ == "__main__":
    {"embed": lambda: embed(sys.argv[2]), "sheet": lambda: sheet(sys.argv[2], sys.argv[3], int(sys.argv[4]))}[sys.argv[1]]()
