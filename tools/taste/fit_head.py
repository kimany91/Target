"""Fits the beauty head (logistic regression on aesthetic probes) from the user's labelled examples, for each model,
and stores it in taste_<model>.json together with the probes. Examples: ex/good__*.jpg, ex/bad__*.jpg (+ imgs/ RL knits)."""
import glob, json, numpy as np, clip
from PIL import Image
from aes import PROBES, TEMPLATE
FEATS = ["loud_colours", "printed_hoodie", "classic_piece", "tonal", "noble", "patina", "intarsia_knit", "busy_print", "big_logo", "stylish"]
EXTRA_GOOD = ["imgs/A_red_rl_cable_knit.jpg", "imgs/A_black_rl_knit.jpg", "imgs/A_brown_rl_knit.jpg"]
def fit(X, y, lam=0.1):
    w = np.zeros(X.shape[1]); b = 0.0
    for _ in range(4000):
        p = 1 / (1 + np.exp(-(X @ w + b))); w -= 0.1 * (X.T @ (p - y) / len(y) + lam * w); b -= 0.1 * (p - y).mean()
    return w, b
for D in ["s2", "s0"]:
    clip.D = D; clip._vis.clear(); clip._txt = None; clip.tok = clip.Tokenizer.from_file(f"{D}/tokenizer.json")
    files = sorted(glob.glob("ex/*.jpg")) + EXTRA_GOOD
    y = np.array([0.0 if "bad__" in f else 1.0 for f in files])
    variant = "vision_model_fp16" if D == "s2" else "vision_model"
    E = clip.img_emb([Image.open(f) for f in files], variant)
    pr = {n: (p, q) for n, p, q in PROBES}
    cols = []
    for f in FEATS:
        p, q = pr[f]
        P = clip.text_emb([t.format(x) for x in p for t in TEMPLATE]).mean(0); N = clip.text_emb([t.format(x) for x in q for t in TEMPLATE]).mean(0)
        cols.append(100 * (E @ (P / np.linalg.norm(P)) - E @ (N / np.linalg.norm(N))))
    X = np.stack(cols, 1); mu = X.mean(0); sd = X.std(0); Z = (X - mu) / sd
    loo = sum(((Z[i] @ w + b) > 0) == bool(y[i]) for i in range(len(y)) for (w, b) in [fit(np.delete(Z, i, 0), np.delete(y, i))])
    w, b = fit(Z, y)
    t = json.load(open(f"taste_{D}.json"))
    t["head"] = {"features": FEATS, "mean": [round(float(v), 4) for v in mu], "std": [round(float(v), 4) for v in sd],
                 "w": [round(float(v), 4) for v in w], "b": round(float(b), 4), "examples": len(y), "looAccuracy": round(loo / len(y), 3)}
    json.dump(t, open(f"taste_{D}.json", "w"), separators=(",", ":"))
    print(D, "LOO", loo, "/", len(y))
    if D == "s2":
        json.dump({f.split("/")[-1][:-4]: [round(float(v), 5) for v in e] for f, e in zip(files, E)}, open("s2_taste_examples.json", "w"))
