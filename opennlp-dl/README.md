# OpenNLP DL

This module provides OpenNLP interface implementations for ONNX models using the `onnxruntime` dependency.

**Important**: This does not provide the ability to train models. Model training is done outside of OpenNLP. This code provides the ability to use ONNX models from OpenNLP.

Models used in the tests are available in the [opennlp evaluation test data](https://nightlies.apache.org/opennlp/opennlp-data.zip) location.

## NameFinderDL

Export a Huggingface NER model to ONNX, e.g.:

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
