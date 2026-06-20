"""
Generate a minimal sms_classifier.tflite model compatible with TflitePatternGenerator.

Input:  int32[1, 64]   — char-hash token ids (padded/truncated to 64)
Output: float32[1, 3]  — softmax probs [DEBIT, CREDIT, IRRELEVANT]

Weights are RANDOM — the model loads and runs but predictions are untrained.
Use this to prove the full TFLite pipeline works in the debug screen.
For real accuracy, train on labelled SMS data and replace this file.

Usage:
    pip install tensorflow
    python scripts/generate_model.py
"""

import os
import tensorflow as tf
import numpy as np

VOCAB_SIZE = 32768   # must match TflitePatternGenerator (0x7FFF + 1)
SEQ_LEN   = 64       # must match TflitePatternGenerator.MAX_LEN
NUM_LABELS = 3       # DEBIT / CREDIT / IRRELEVANT

print(f"TensorFlow {tf.__version__}")

# int32 token ids → cast to float → two dense layers → softmax
inp = tf.keras.Input(shape=(SEQ_LEN,), dtype=tf.int32, name="token_ids")
x   = tf.cast(inp, tf.float32) / VOCAB_SIZE   # normalise 0..1
x   = tf.keras.layers.Dense(32, activation="relu")(x)
x   = tf.keras.layers.Dense(NUM_LABELS, activation="softmax")(x)
model = tf.keras.Model(inp, x)
model.compile()
model.summary()

# Convert to TFLite
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]
tflite_bytes = converter.convert()

out_dir  = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets")
out_path = os.path.join(out_dir, "sms_classifier.tflite")
os.makedirs(out_dir, exist_ok=True)

with open(out_path, "wb") as f:
    f.write(tflite_bytes)

print(f"\nWrote {out_path}  ({len(tflite_bytes) / 1024:.1f} KB)")
print("Model has RANDOM weights — replace with a trained checkpoint for real accuracy.")

# Quick sanity check: run the model via the TFLite interpreter
interp = tf.lite.Interpreter(model_content=tflite_bytes)
interp.allocate_tensors()
inp_detail = interp.get_input_details()[0]
out_detail = interp.get_output_details()[0]
print(f"\nSanity check:")
print(f"  Input  shape={inp_detail['shape']}  dtype={inp_detail['dtype']}")
print(f"  Output shape={out_detail['shape']}  dtype={out_detail['dtype']}")

test_tokens = np.zeros((1, SEQ_LEN), dtype=np.int32)
test_tokens[0, :5] = [1234, 5678, 9012, 3456, 7890]   # fake token ids
interp.set_tensor(inp_detail["index"], test_tokens)
interp.invoke()
probs = interp.get_tensor(out_detail["index"])[0]
labels = ["DEBIT", "CREDIT", "IRRELEVANT"]
print(f"  Output probs: {dict(zip(labels, probs.round(3)))}")
print(f"  Predicted: {labels[probs.argmax()]}")
print("\nAll good — run: .\\gradlew installDebug")
