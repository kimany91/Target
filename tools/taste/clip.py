import numpy as np, onnxruntime as ort, json, sys
from PIL import Image
from tokenizers import Tokenizer
D = "s2"
tok = Tokenizer.from_file(f"{D}/tokenizer.json")
_vis = {}
def vis(variant="vision_model"):
    if variant not in _vis: _vis[variant] = ort.InferenceSession(f"{D}/onnx/{variant}.onnx", providers=["CPUExecutionProvider"])
    return _vis[variant]
_txt = None
def txt():
    global _txt
    if _txt is None: _txt = ort.InferenceSession(f"{D}/onnx/text_model.onnx", providers=["CPUExecutionProvider"])
    return _txt
def ids(texts):
    out = np.zeros((len(texts), 77), dtype=np.int64)
    for i, t in enumerate(texts):
        e = tok.encode(t.lower()).ids[:77]
        out[i, :len(e)] = e
    return out
def text_emb(texts):
    e = txt().run(None, {"input_ids": ids(texts)})[0]
    return e / np.linalg.norm(e, axis=1, keepdims=True)
def prep(img):
    img = img.convert("RGB"); w, h = img.size; s = 256 / min(w, h)
    img = img.resize((max(256, round(w * s)), max(256, round(h * s))), Image.BILINEAR)
    w, h = img.size; l = (w - 256) // 2; t = (h - 256) // 2
    a = np.asarray(img.crop((l, t, l + 256, t + 256)), dtype=np.float32) / 255.0
    return a.transpose(2, 0, 1)
def img_emb(imgs, variant="vision_model"):
    x = np.stack([prep(i) for i in imgs])
    e = vis(variant).run(None, {"pixel_values": x})[0]
    return e / np.linalg.norm(e, axis=1, keepdims=True)
