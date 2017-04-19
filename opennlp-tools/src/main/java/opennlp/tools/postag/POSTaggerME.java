/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.postag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.ml.BeamSearch;
import opennlp.tools.ml.EventModelSequenceTrainer;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.SequenceTrainer;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.ml.TrainerFactory.TrainerType;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.SequenceClassificationModel;
import opennlp.tools.ngram.NGramModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Sequence;
import opennlp.tools.util.SequenceValidator;
import opennlp.tools.util.StringList;
import opennlp.tools.util.StringUtil;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.featuregen.StringPattern;

/**
 * A part-of-speech tagger that uses maximum entropy.  Tries to predict whether
 * words are nouns, verbs, or any of 70 other POS tags depending on their
 * surrounding context.
 *
 */
public class POSTaggerME implements POSTagger {

  public static final int DEFAULT_BEAM_SIZE = 3;

  private POSModel modelPackage;

  /**
   * The feature context generator.
   */
  protected POSContextGenerator contextGen;

  /**
   * Tag dictionary used for restricting words to a fixed set of tags.
   */
  protected TagDictionary tagDictionary;

  protected Dictionary ngramDictionary;

  /**
   * Says whether a filter should be used to check whether a tag assignment
   * is to a word outside of a closed class.
   */
  protected boolean useClosedClassTagsFilter = false;


  /**
   * The size of the beam to be used in determining the best sequence of pos tags.
   */
  protected int size;

  private Sequence bestSequence;

  private SequenceClassificationModel<String> model;

  private SequenceValidator<String> sequenceValidator;

  /**
   * Initializes the current instance with the provided model.
   *
   * @param model
   */
  public POSTaggerME(POSModel model) {
    POSTaggerFactory factory = model.getFactory();

    int beamSize = POSTaggerME.DEFAULT_BEAM_SIZE;

    String beamSizeString = model.getManifestProperty(BeamSearch.BEAM_SIZE_PARAMETER);

    if (beamSizeString != null) {
      beamSize = Integer.parseInt(beamSizeString);
    }

    modelPackage = model;

    contextGen = factory.getPOSContextGenerator(beamSize);
    tagDictionary = factory.getTagDictionary();
    size = beamSize;

    sequenceValidator = factory.getSequenceValidator();

    if (model.getPosSequenceModel() != null) {
      this.model = model.getPosSequenceModel();
    }
    else {
      this.model = new opennlp.tools.ml.BeamSearch<>(beamSize,
          model.getPosModel(), 0);
    }

  }

  /**
   * Retrieves an array of all possible part-of-speech tags from the
   * tagger.
   *
   * @return String[]
   */
  public String[] getAllPosTags() {
    return model.getOutcomes();
  }

  public String[] tag(String[] sentence) {
    return this.tag(sentence, null);
  }

  public String[] tag(String[] sentence, Object[] additionaContext) {
    bestSequence = model.bestSequence(sentence, additionaContext, contextGen, sequenceValidator);
    List<String> t = bestSequence.getOutcomes();
    return t.toArray(new String[t.size()]);
  }

  /**
   * Returns at most the specified number of taggings for the specified sentence.
   *
   * @param numTaggings The number of tagging to be returned.
   * @param sentence An array of tokens which make up a sentence.
   *
   * @return At most the specified number of taggings for the specified sentence.
   */
  public String[][] tag(int numTaggings, String[] sentence) {
    Sequence[] bestSequences = model.bestSequences(numTaggings, sentence, null,
        contextGen, sequenceValidator);
    String[][] tags = new String[bestSequences.length][];
    for (int si = 0; si < tags.length; si++) {
      List<String> t = bestSequences[si].getOutcomes();
      tags[si] = t.toArray(new String[t.size()]);
    }
    return tags;
  }

  public Sequence[] topKSequences(String[] sentence) {
    return this.topKSequences(sentence, null);
  }

  public Sequence[] topKSequences(String[] sentence, Object[] additionaContext) {
    return model.bestSequences(size, sentence, additionaContext, contextGen, sequenceValidator);
  }

  /**
   * Populates the specified array with the probabilities for each tag of the last tagged sentence.
   *
   * @param probs An array to put the probabilities into.
   */
  public void probs(double[] probs) {
    bestSequence.getProbs(probs);
  }

  /**
   * Returns an array with the probabilities for each tag of the last tagged sentence.
   *
   * @return an array with the probabilities for each tag of the last tagged sentence.
   */
  public double[] probs() {
    return bestSequence.getProbs();
  }

  public String[] getOrderedTags(List<String> words, List<String> tags, int index) {
    return getOrderedTags(words,tags,index,null);
  }

  public String[] getOrderedTags(List<String> words, List<String> tags, int index,double[] tprobs) {

    if (modelPackage.getPosModel() != null) {

      MaxentModel posModel = modelPackage.getPosModel();

      double[] probs = posModel.eval(contextGen.getContext(index,
          words.toArray(new String[words.size()]),
          tags.toArray(new String[tags.size()]),null));

      String[] orderedTags = new String[probs.length];
      for (int i = 0; i < probs.length; i++) {
        int max = 0;
        for (int ti = 1; ti < probs.length; ti++) {
          if (probs[ti] > probs[max]) {
            max = ti;
          }
        }
        orderedTags[i] = posModel.getOutcome(max);
        if (tprobs != null) {
          tprobs[i] = probs[max];
        }
        probs[max] = 0;
      }
      return orderedTags;
    }
    else {
      throw new UnsupportedOperationException("This method can only be called if the "
          + "classifcation model is an event model!");
    }
  }

  public static POSModel train(String languageCode,
      ObjectStream<POSSample> samples, TrainingParameters trainParams,
      POSTaggerFactory posFactory) throws IOException {

    int beamSize = trainParams.getIntParameter(BeamSearch.BEAM_SIZE_PARAMETER, POSTaggerME.DEFAULT_BEAM_SIZE);

    POSContextGenerator contextGenerator = posFactory.getPOSContextGenerator();

    Map<String, String> manifestInfoEntries = new HashMap<>();

    TrainerType trainerType = TrainerFactory.getTrainerType(trainParams);

    MaxentModel posModel = null;
    SequenceClassificationModel<String> seqPosModel = null;
    if (TrainerType.EVENT_MODEL_TRAINER.equals(trainerType)) {
      ObjectStream<Event> es = new POSSampleEventStream(samples, contextGenerator);

      EventTrainer trainer = TrainerFactory.getEventTrainer(trainParams,
          manifestInfoEntries);
      posModel = trainer.train(es);
    }
    else if (TrainerType.EVENT_MODEL_SEQUENCE_TRAINER.equals(trainerType)) {
      POSSampleSequenceStream ss = new POSSampleSequenceStream(samples, contextGenerator);
      EventModelSequenceTrainer trainer =
          TrainerFactory.getEventModelSequenceTrainer(trainParams, manifestInfoEntries);
      posModel = trainer.train(ss);
    }
    else if (TrainerType.SEQUENCE_TRAINER.equals(trainerType)) {
      SequenceTrainer trainer = TrainerFactory.getSequenceModelTrainer(
          trainParams, manifestInfoEntries);

      // TODO: This will probably cause issue, since the feature generator uses the outcomes array

      POSSampleSequenceStream ss = new POSSampleSequenceStream(samples, contextGenerator);
      seqPosModel = trainer.train(ss);
    }
    else {
      throw new IllegalArgumentException("Trainer type is not supported: " + trainerType);
    }

    if (posModel != null) {
      return new POSModel(languageCode, posModel, beamSize, manifestInfoEntries, posFactory);
    }
    else {
      return new POSModel(languageCode, seqPosModel, manifestInfoEntries, posFactory);
    }
  }

  public static Dictionary buildNGramDictionary(ObjectStream<POSSample> samples, int cutoff)
      throws IOException {

    NGramModel ngramModel = new NGramModel();

    POSSample sample;
    while ((sample = samples.read()) != null) {
      String[] words = sample.getSentence();

      if (words.length > 0)
        ngramModel.add(new StringList(words), 1, 1);
    }

    ngramModel.cutoff(cutoff, Integer.MAX_VALUE);

    return ngramModel.toDictionary(true);
  }

  public static void populatePOSDictionary(ObjectStream<POSSample> samples,
      MutableTagDictionary dict, int cutoff) throws IOException {
    System.out.println("Expanding POS Dictionary ...");
    long start = System.nanoTime();

    // the data structure will store the word, the tag, and the number of
    // occurrences
    Map<String, Map<String, AtomicInteger>> newEntries = new HashMap<>();
    POSSample sample;
    while ((sample = samples.read()) != null) {
      String[] words = sample.getSentence();
      String[] tags = sample.getTags();

      for (int i = 0; i < words.length; i++) {
        // only store words
        if (!StringPattern.recognize(words[i]).containsDigit()) {
          String word;
          if (dict.isCaseSensitive()) {
            word = words[i];
          } else {
            word = StringUtil.toLowerCase(words[i]);
          }

          if (!newEntries.containsKey(word)) {
            newEntries.put(word, new HashMap<>());
          }

          String[] dictTags = dict.getTags(word);
          if (dictTags != null) {
            for (String tag : dictTags) {
              // for this tags we start with the cutoff
              Map<String, AtomicInteger> value = newEntries.get(word);
              if (!value.containsKey(tag)) {
                value.put(tag, new AtomicInteger(cutoff));
              }
            }
          }

          if (!newEntries.get(word).containsKey(tags[i])) {
            newEntries.get(word).put(tags[i], new AtomicInteger(1));
          } else {
            newEntries.get(word).get(tags[i]).incrementAndGet();
          }
        }
      }
    }

    // now we check if the word + tag pairs have enough occurrences, if yes we
    // add it to the dictionary
    for (Entry<String, Map<String, AtomicInteger>> wordEntry : newEntries
        .entrySet()) {
      List<String> tagsForWord = new ArrayList<>();
      for (Entry<String, AtomicInteger> entry : wordEntry.getValue().entrySet()) {
        if (entry.getValue().get() >= cutoff) {
          tagsForWord.add(entry.getKey());
        }
      }
      if (tagsForWord.size() > 0) {
        dict.put(wordEntry.getKey(),
            tagsForWord.toArray(new String[tagsForWord.size()]));
      }
    }

    System.out.println("... finished expanding POS Dictionary. ["
        + (System.nanoTime() - start) / 1000000 + "ms]");
  }
}
