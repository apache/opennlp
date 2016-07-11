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
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

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
import opennlp.tools.util.model.ModelType;

/**
 * A part-of-speech tagger that uses maximum entropy.  Tries to predict whether
 * words are nouns, verbs, or any of 70 other POS tags depending on their
 * surrounding context.
 *
 */
public class POSTaggerME implements POSTagger {

  private static final Logger LOGGER = LoggerFactory.getLogger(POSTaggerME.class);

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
   * Initializes the current instance with the provided
   * model and provided beam size.
   *
   * @param model
   * @param beamSize
   *
   * @deprecated the beam size should be specified in the params during training
   */
  @Deprecated
  public POSTaggerME(POSModel model, int beamSize, int cacheSize) {
    POSTaggerFactory factory = model.getFactory();

    modelPackage = model;

    // TODO: Why is this the beam size?! not cache size?
    contextGen = factory.getPOSContextGenerator(beamSize);
    tagDictionary = factory.getTagDictionary();
    size = beamSize;

    sequenceValidator = factory.getSequenceValidator();

    if (model.getPosSequenceModel() != null) {
      this.model = model.getPosSequenceModel();
    }
    else {
      this.model = new opennlp.tools.ml.BeamSearch<String>(beamSize,
          model.getPosModel(), cacheSize);
    }
  }

  /**
   * Initializes the current instance with the provided model
   * and the default beam size of 3.
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
      this.model = new opennlp.tools.ml.BeamSearch<String>(beamSize,
          model.getPosModel(), 0);
    }

  }

  /**
   * Returns the number of different tags predicted by this model.
   *
   * @return the number of different tags predicted by this model.
   * @deprecated use getAllPosTags instead!
   */
  @Deprecated
  public int getNumTags() {

    // TODO: Lets discuss on the dev list how to do this properly!
    // Nobody needs the number of tags, if the tags are not available.

    return model.getOutcomes().length;
  }

  /**
   * Retrieves an array of all possible part-of-speech tags from the
   * tagger.
   *
   * @return
   */
  public String[] getAllPosTags() {
    return model.getOutcomes();
  }

  @Deprecated
  public List<String> tag(List<String> sentence) {
    bestSequence = model.bestSequence(sentence.toArray(new String[sentence.size()]), null, contextGen, sequenceValidator);
    return bestSequence.getOutcomes();
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
    for (int si=0;si<tags.length;si++) {
      List<String> t = bestSequences[si].getOutcomes();
      tags[si] = t.toArray(new String[t.size()]);
    }
    return tags;
  }

  @Deprecated
  public Sequence[] topKSequences(List<String> sentence) {
    return model.bestSequences(size, sentence.toArray(new String[sentence.size()]), null,
        contextGen, sequenceValidator);
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

  @Deprecated
  public String tag(String sentence) {
    List<String> toks = new ArrayList<String>();
    StringTokenizer st = new StringTokenizer(sentence);
    while (st.hasMoreTokens())
      toks.add(st.nextToken());
    List<String> tags = tag(toks);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < tags.size(); i++)
      sb.append(toks.get(i) + "/" + tags.get(i) + " ");
    return sb.toString().trim();
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
        if (tprobs != null){
          tprobs[i]=probs[max];
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

    String beamSizeString = trainParams.getSettings().get(BeamSearch.BEAM_SIZE_PARAMETER);

    int beamSize = POSTaggerME.DEFAULT_BEAM_SIZE;
    if (beamSizeString != null) {
      beamSize = Integer.parseInt(beamSizeString);
    }

    POSContextGenerator contextGenerator = posFactory.getPOSContextGenerator();

    Map<String, String> manifestInfoEntries = new HashMap<String, String>();

    TrainerType trainerType = TrainerFactory.getTrainerType(trainParams.getSettings());

    MaxentModel posModel = null;
    SequenceClassificationModel<String> seqPosModel = null;
    if (TrainerType.EVENT_MODEL_TRAINER.equals(trainerType)) {
      ObjectStream<Event> es = new POSSampleEventStream(samples, contextGenerator);

      EventTrainer trainer = TrainerFactory.getEventTrainer(trainParams.getSettings(),
          manifestInfoEntries);
      posModel = trainer.train(es);
    }
    else if (TrainerType.EVENT_MODEL_SEQUENCE_TRAINER.equals(trainerType)) {
      POSSampleSequenceStream ss = new POSSampleSequenceStream(samples, contextGenerator);
      EventModelSequenceTrainer trainer = TrainerFactory.getEventModelSequenceTrainer(trainParams.getSettings(),
          manifestInfoEntries);
      posModel = trainer.train(ss);
    }
    else if (TrainerType.SEQUENCE_TRAINER.equals(trainerType)) {
      SequenceTrainer trainer = TrainerFactory.getSequenceModelTrainer(
          trainParams.getSettings(), manifestInfoEntries);

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

  /**
   * @deprecated use
   *             {@link #train(String, ObjectStream, TrainingParameters, POSTaggerFactory)}
   *             instead and pass in a {@link POSTaggerFactory}.
   */
  public static POSModel train(String languageCode, ObjectStream<POSSample> samples, TrainingParameters trainParams,
      POSDictionary tagDictionary, Dictionary ngramDictionary) throws IOException {

    return train(languageCode, samples, trainParams, new POSTaggerFactory(
        ngramDictionary, tagDictionary));
  }

  /**
   * @deprecated use
   *             {@link #train(String, ObjectStream, TrainingParameters, POSTaggerFactory)}
   *             instead and pass in a {@link POSTaggerFactory} and a
   *             {@link TrainingParameters}.
   */
  @Deprecated
  public static POSModel train(String languageCode, ObjectStream<POSSample> samples, ModelType modelType, POSDictionary tagDictionary,
      Dictionary ngramDictionary, int cutoff, int iterations) throws IOException {

    TrainingParameters params = new TrainingParameters();

    params.put(TrainingParameters.ALGORITHM_PARAM, modelType.toString());
    params.put(TrainingParameters.ITERATIONS_PARAM, Integer.toString(iterations));
    params.put(TrainingParameters.CUTOFF_PARAM, Integer.toString(cutoff));

    return train(languageCode, samples, params, tagDictionary, ngramDictionary);
  }

  public static Dictionary buildNGramDictionary(ObjectStream<POSSample> samples, int cutoff)
      throws IOException {

    NGramModel ngramModel = new NGramModel();

    POSSample sample;
    while((sample = samples.read()) != null) {
      String[] words = sample.getSentence();

      if (words.length > 0)
        ngramModel.add(new StringList(words), 1, 1);
    }

    ngramModel.cutoff(cutoff, Integer.MAX_VALUE);

    return ngramModel.toDictionary(true);
  }

  public static void populatePOSDictionary(ObjectStream<POSSample> samples,
      MutableTagDictionary dict, int cutoff) throws IOException {
    LOGGER.trace("Expanding POS Dictionary ...");
    long start = System.nanoTime();

    // the data structure will store the word, the tag, and the number of
    // occurrences
    Map<String, Map<String, AtomicInteger>> newEntries = new HashMap<String, Map<String, AtomicInteger>>();
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
            newEntries.put(word, new HashMap<String, AtomicInteger>());
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
      List<String> tagsForWord = new ArrayList<String>();
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

    LOGGER.trace("... finished expanding POS Dictionary. ["
        + (System.nanoTime() - start) / 1000000 + "ms]");
  }
}
