# OpenNLP DL

This module provides OpenNLP interface implementations for ONNX models using the `onnxruntime` dependency.

**Important**: This does not provide the ability to train models. Model training is done outside of OpenNLP. This code provides the ability to use ONNX models from OpenNLP.

Models used in the tests are available in the [opennlp evaluation test data](https://nightlies.apache.org/opennlp/opennlp-data.zip) location.

## NameFinderDL

`NameFinderDL` runs ONNX token-classification models that use BIO labels. Any
label in the form `B-<TYPE>` starts an entity and subsequent `I-<TYPE>` labels
continue that entity. The text after the prefix is reported as the OpenNLP span
type, for example `B-PER` and `I-PER` produce spans with type `PER`.

The finder uses BERT basic tokenization followed by WordPiece tokenization and
then maps the reconstructed WordPiece text back to the caller's original input
so returned spans can be used with `Span#getCoveredText(...)`. Span probabilities
are normalized from the model logits and are reported in the range `(0, 1]`.

Named entity models are commonly cased, so lower casing is disabled by default.
Set `InferenceOptions#setLowerCase(true)` only for models trained with uncased
input.

Export a Hugging Face NER model to ONNX, e.g.:

```bash
python -m transformers.onnx --model=dslim/bert-base-NER --feature token-classification exported
```

## DocumentCategorizerDL

Export a Huggingface classification (e.g. sentiment) model to ONNX, e.g.:

```bash
python -m transformers.onnx --model=nlptown/bert-base-multilingual-uncased-sentiment --feature sequence-classification exported
```

## SentenceVectors

Convert a sentence vectors model to ONNX, e.g.:

Install dependencies:

```bash
python3 -m pip install optimum onnx onnxruntime
```

Convert the model:

```python
from optimum.onnxruntime import ORTModelForFeatureExtraction
from transformers import AutoTokenizer
from pathlib import Path


model_id="sentence-transformers/all-MiniLM-L6-v2"
onnx_path = Path("onnx")

# load vanilla transformers and convert to onnx
model = ORTModelForFeatureExtraction.from_pretrained(model_id, from_transformers=True)
tokenizer = AutoTokenizer.from_pretrained(model_id)

# save onnx checkpoint and tokenizer
model.save_pretrained(onnx_path)
tokenizer.save_pretrained(onnx_path)
```
