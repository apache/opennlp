# OpenNLP DL

This module provides OpenNLP interface implementations for ONNX models using the `onnxruntime` dependency.

## TokenNameFinder

* Export a Huggingface NER model to ONNX, e.g.:

```
python -m transformers.onnx --model=dslim/bert-base-NER --feature token-classification exported
```

* Copy the exported model to `src/test/resources/namefinder/model.onnx`.
* Copy the model's [vocab.txt](https://huggingface.co/dslim/bert-base-NER/tree/main) to `src/test/resources/namefinder/vocab.txt`.

Now you can run the tests in `NameFinderDLTest`.

## DocumentCategorizer

* Export a Huggingface classification (e.g. sentiment) model to ONNX, e.g.:

```
python -m transformers.onnx --model=nlptown/bert-base-multilingual-uncased-sentiment --feature sequence-classification exported
```

* Copy the exported model to `src/test/resources/doccat/model.onnx`.
* Copy the model's [vocab.txt](https://huggingface.co/nlptown/bert-base-multilingual-uncased-sentiment/tree/main) to `src/test/resources/namefinder/vocab.txt`.

Now you can run the tests in `DocumentCategorizerDLTest`.