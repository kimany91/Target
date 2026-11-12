import json, numpy as np, clip
from cats import CATS, TEMPLATES
from aes import PROBES, TEMPLATE
from styles_prompts import STYLES
META = {
 "s2": dict(name="MobileCLIP-S2", url="https://huggingface.co/Xenova/mobileclip_s2/resolve/0c311c620b36a2270b851db7bef9135f3eaae5d7/onnx/vision_model_fp16.onnx",
            sha256="e011629d1490897f039fd8fbf1d6c677a03e0ffd1b5cea7cec5099bd8fea9476", bytes=71710175),
 "s0": dict(name="MobileCLIP-S0", url="https://huggingface.co/Xenova/mobileclip_s0/resolve/757d59c9c6870a76a4b0306f05f5061bca15c39f/onnx/vision_model.onnx",
            sha256="17d3c037b1d488c10c50e09f6009ea5a198caef4e0e8f4ea5617b7cb2d067ac0", bytes=45543630),
}
def mean(prompts):
    e = clip.text_emb(prompts).mean(0); return e / np.linalg.norm(e)
def r(v): return [round(float(x), 5) for x in v]
for D in ["s2", "s0"]:
    clip.D = D; clip._txt = None; clip.tok = clip.Tokenizer.from_file(f"{D}/tokenizer.json")
    out = dict(META[D]); out.update(size=256, dim=512, logitScale=100.0)
    out["categories"] = {c: r(mean([t.format(p) for p in ps for t in TEMPLATES])) for c, ps in CATS.items()}
    out["probes"] = {n: {"pos": r(mean([t.format(x) for x in pos for t in TEMPLATE])), "neg": r(mean([t.format(x) for x in neg for t in TEMPLATE]))} for n, pos, neg in PROBES}
    out["styles"] = {s: r(mean([t.format(p) for p in ps for t in TEMPLATES])) for s, ps in STYLES.items()}
    json.dump(out, open(f"taste_{D}.json", "w"), separators=(",", ":"))
    print(D, "ok")
