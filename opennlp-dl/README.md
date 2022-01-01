# OpenNLP DL

This module provides OpenNLP interface implementations for ONNX models.

## TokenNameFinder

* Export a Huggingface model to ONNX:

```
python -m transformers.onnx --model=dslim/bert-base-NER --feature token-classification exported
```

* Copy the exported module to `src/test/resources/namefinder/model.onnx`.
* Copy the model's [vocab.txt](https://huggingface.co/dslim/bert-base-NER/tree/main) to `src/test/resources/namefinder/vocab.txt`.

Now you can run the tests in `NameFinderDLTest`.

## DocumentCategorizer