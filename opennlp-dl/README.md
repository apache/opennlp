# OpenNLP DL

This module provides OpenNLP interface implementations for ONNX models using the `onnxruntime` dependency.

**Important**: This does not provide the ability to train models. Model training is done outside of OpenNLP. This code provides the ability to use ONNX models from OpenNLP.

To build with example models, download the models to the `/src/test/resources` directory. (These are the exported models described below.)

```

export OPENNLP_DATA=/tmp/
mkdir /tmp/dl-doccat /tmp/dl-namefinder

# Document categorizer model
wget https://www.dropbox.com/s/n9uzs8r4xm9rhxb/model.onnx?dl=0 -O $OPENNLP_DATA/dl-doccat/model.onnx
wget https://www.dropbox.com/s/aw6yjc68jw0jts6/vocab.txt?dl=0 -O $OPENNLP_DATA/dl-doccat/vocab.txt

# Namefinder model
wget https://www.dropbox.com/s/zgogq65gs9tyfm1/model.onnx?dl=0 -O $OPENNLP_DATA/dl-namefinder/model.onnx
wget https://www.dropbox.com/s/3byt1jggly1dg98/vocab.txt?dl=0 -O $OPENNLP_DATA/dl-/namefinder/vocab.txt
```

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