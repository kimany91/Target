# Taste data (generated offline)

`app/src/main/assets/taste_s2.json` / `taste_s0.json` hold the MobileCLIP **text** embeddings Target needs on the phone:
garment categories, aesthetic probes (big logo, neon, wrinkled, damaged, bad photo, noble material, patina, stylish)
and fashion styles. Only the vision encoder runs on the device; texts are encoded here once.

Regenerate (needs `pip install onnxruntime tokenizers pillow numpy` and the Xenova MobileCLIP ONNX exports in `s2/`, `s0/`):

```bash
python3 gen.py
```

Prompts live in `cats.py`, `aes.py` and `styles_prompts.py`.
