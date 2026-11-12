import json, numpy as np, clip
from cats import CATS, TEMPLATES
from aes import PROBES, TEMPLATE
from styles_prompts import STYLES
META = {
 # fp32, not fp16: on ARM phones ONNX Runtime really computes an fp16 graph in half precision (NaN embeddings and
 # drift from the offline calibration); on x86 it silently upcasts. Same weights: cosine 0.99996 with the fp16 file.
 "s2": dict(name="MobileCLIP-S2 fp32", url="https://huggingface.co/Xenova/mobileclip_s2/resolve/0c311c620b36a2270b851db7bef9135f3eaae5d7/onnx/vision_model.onnx",
            sha256="d28b92d7a3a6ba99bd000cce5c91678c0e279dc934c887a3785908a811872a6c", bytes=143020962),
 "s0": dict(name="MobileCLIP-S0", url="https://huggingface.co/Xenova/mobileclip_s0/resolve/757d59c9c6870a76a4b0306f05f5061bca15c39f/onnx/vision_model.onnx",
            sha256="17d3c037b1d488c10c50e09f6009ea5a198caef4e0e8f4ea5617b7cb2d067ac0", bytes=45543630),
}
# per-model decision offsets (logit + offset = 0 at the boundary), calibrated with tools/taste/README.md "knitwear"
OFFSET = {"s2": {"knitwear": 3.5}, "s0": {"knitwear": 2.0}}
def mean(prompts):
    e = clip.text_emb(prompts).mean(0); return e / np.linalg.norm(e)
def r(v): return [round(float(x), 5) for x in v]
for D in ["s2", "s0"]:
    clip.D = D; clip._txt = None; clip.tok = clip.Tokenizer.from_file(f"{D}/tokenizer.json")
    out = dict(META[D]); out.update(size=256, dim=512, logitScale=100.0)
    out["categories"] = {c: r(mean([t.format(p) for p in ps for t in TEMPLATES])) for c, ps in CATS.items()}
    out["probes"] = {n: {"pos": r(mean([t.format(x) for x in pos for t in TEMPLATE])), "neg": r(mean([t.format(x) for x in neg for t in TEMPLATE])), **({"offset": OFFSET[D][n]} if n in OFFSET[D] else {})} for n, pos, neg in PROBES}
    out["styles"] = {s: r(mean([t.format(p) for p in ps for t in TEMPLATES])) for s, ps in STYLES.items()}
    json.dump(out, open(f"taste_{D}.json", "w"), separators=(",", ":"))
    print(D, "ok")
