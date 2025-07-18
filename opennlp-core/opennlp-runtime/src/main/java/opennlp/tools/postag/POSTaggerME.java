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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.ml.BeamSearch;
import opennlp.tools.ml.EventModelSequenceTrainer;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.Probabilistic;
import opennlp.tools.ml.SequenceTrainer;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.ml.TrainerFactory.TrainerType;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.SequenceClassificationModel;
import opennlp.tools.models.ModelType;
import opennlp.tools.ngram.NGramModel;
import opennlp.tools.util.DownloadUtil;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Sequence;
import opennlp.tools.util.SequenceValidator;
import opennlp.tools.util.StringList;
import opennlp.tools.util.StringUtil;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.featuregen.StringPattern;

/**
 * A {@link POSTagger part-of-speech tagger} implementation that uses maximum entropy.
 * <p>
 * Tries to predict whether words are nouns, verbs, or any other {@link POSTagFormat POS tags}
 * depending on their surrounding context.
 *
 * @see POSModel
 * @see POSTagFormat
 * @see POSTagger
 * @see Probabilistic
 */
public class POSTaggerME implements POSTagger, Probabilistic {

  private static final Logger logger = LoggerFactory.getLogger(POSTaggerME.class);

  /**
   * The default beam size value is 3.
   */
  public static final int DEFAULT_BEAM_SIZE = 3;

  private final POSModel modelPackage;

  /**
   * The {@link POSContextGenerator feature context generator}.
   */
  protected final POSContextGenerator cg;

  /**
   * {@link TagDictionary} used for restricting words to a fixed set of tags.
   */
  protected final TagDictionary tagDictionary;

  /**
   * The size of the beam to be used in determining the best sequence of pos tags.
   */
  protected final int size;

  private Sequence bestSequence;

  private final SequenceClassificationModel model;

  private final SequenceValidator<String> sequenceValidator;

  private final POSTagFormat posTagFormat;

  protected final POSTagFormatMapper posTagFormatMapper;

  /**
   * Initializes a {@link POSTaggerME} by downloading a default model for a given
   * {@code language}.
   *
   * @param language An ISO conform language code.
   * @throws IOException Thrown if the model could not be downloaded or saved.
   */
  public POSTaggerME(String language) throws IOException {
    this(language, POSTagFormat.UD);
  }

  /**
   * Initializes a {@link POSTaggerME} by downloading a default model for a given
   * {@code language}.
   *
   * @param language An ISO conform language code.
   * @param format   A valid {@link POSTagFormat}.
   * @throws IOException Thrown if the model could not be downloaded or saved.
   */
  public POSTaggerME(String language, POSTagFormat format) throws IOException {
    this(DownloadUtil.downloadModel(language, ModelType.POS, POSModel.class), format);
  }

  /**
   * Initializes a {@link POSTaggerME} with the provided {@link POSModel model}.
   *
   * @param model A valid {@link POSModel}.
   */
  public POSTaggerME(POSModel model) {
    this(model, POSTagFormat.UD);
  }

  /**
   * Initializes a {@link POSTaggerME} with the provided {@link POSModel model}.
   *
   * @param model  A valid {@link POSModel}.
   * @param format A valid {@link POSTagFormat}.
   */
  public POSTaggerME(POSModel model, POSTagFormat format) {
    this.posTagFormat = format;
    POSTaggerFactory factory = model.getFactory();

    int beamSize = POSTaggerME.DEFAULT_BEAM_SIZE;

    String beamSizeString = model.getManifestProperty(BeamSearch.BEAM_SIZE_PARAMETER);

    if (beamSizeString != null) {
      beamSize = Integer.parseInt(beamSizeString);
    }

    modelPackage = model;

    cg = factory.getPOSContextGenerator(beamSize);
    tagDictionary = factory.getTagDictionary();
    size = beamSize;

    sequenceValidator = factory.getSequenceValidator();

    if (model.getPosSequenceModel() != null) {
      this.model = model.getPosSequenceModel();
    } else {
      this.model = new BeamSearch(beamSize, model.getArtifact(POSModel.POS_MODEL_ENTRY_NAME), 0);
    }

    this.posTagFormatMapper = (format == POSTagFormat.CUSTOM)
        ? new POSTagFormatMapper.NoOp()
        : new POSTagFormatMapper(getAllPosTags());

  }

  /**
   * @return Retrieves an array of all possible part-of-speech tags from the tagger.
   */
  public String[] getAllPosTags() {
    return model.getOutcomes();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String[] tag(String[] sentence) {
    return this.tag(sentence, null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String[] tag(String[] sentence, Object[] additionalContext) {
    bestSequence = model.bestSequence(sentence, additionalContext, cg, sequenceValidator);
    final List<String> t = bestSequence.getOutcomes();
    return convertTags(t);
  }

  /**
   * Returns at most the specified {@code numTaggings} for the specified {@code sentence}.
   *
   * @param numTaggings The number of tagging to be returned.
   * @param sentence    An array of tokens which make up a sentence.
   * @return At most the specified number of taggings for the specified {@code sentence}.
   */
  public String[][] tag(int numTaggings, String[] sentence) {
    Sequence[] bestSequences = model.bestSequences(numTaggings, sentence, null,
            cg, sequenceValidator);
    String[][] tags = new String[bestSequences.length][];
    for (int si = 0; si < tags.length; si++) {
      List<String> t = bestSequences[si].getOutcomes();
      tags[si] = convertTags(t);
    }
    return tags;
  }

  private String[] convertTags(List<String> t) {
    if (posTagFormat == POSTagFormat.CUSTOM
        || posTagFormatMapper.getGuessedFormat() == posTagFormat) {
      return t.toArray(new String[0]);
    } else {
      return posTagFormatMapper.convertTags(t);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Sequence[] topKSequences(String[] sentence) {
    return this.topKSequences(sentence, null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Sequence[] topKSequences(String[] sentence, Object[] additionalContext) {
    return model.bestSequences(size, sentence, additionalContext, cg, sequenceValidator);
  }

  /**
   * Populates the specified {@code probs} array with the probabilities
   * for each tag of the last tagged sentence.
   *
   * @param probs An array to put the probabilities into.
   */
  public void probs(double[] probs) {
    bestSequence.getProbs(probs);
  }

  /**
   * {@inheritDoc}
   * 
   * The sequence was determined based on the previous call to {@link #tag(String[])}.
   *
   * @return An array with the same number of probabilities as tokens were sent
   *         to {@link #tag(String[])} when it was last called.
   */
  @Override
  public double[] probs() {
    return bestSequence.getProbs();
  }

  public String[] getOrderedTags(List<String> words, List<String> tags, int index) {
    return getOrderedTags(words, tags, index, null);
  }

  public String[] getOrderedTags(List<String> words, List<String> tags, int index, double[] tprobs) {

    MaxentModel posModel = modelPackage.getArtifact(POSModel.POS_MODEL_ENTRY_NAME);
    if (posModel != null) {

      double[] probs = posModel.eval(cg.getContext(index, words.toArray(new String[0]),
          tags.toArray(new String[0]), null));

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
      return convertTags(Arrays.stream(orderedTags).toList());
    } else {
      throw new UnsupportedOperationException("This method can only be called if the "
          + "classification model is an event model!");
    }
  }

  /**
   * Starts a training of a {@link POSModel} with the given parameters.
   *
   * @param languageCode  The ISO language code to train the model. Must not be {@code null}.
   * @param samples       The {@link ObjectStream} of {@link POSSample} used as input for training.
   * @param mlParams      The {@link TrainingParameters} for the context of the training process.
   * @param posFactory    The {@link POSTaggerFactory} for creating related objects as defined
   *                      via {@code mlParams}.
   *
   * @return A valid, trained {@link POSModel} instance.
   * @throws IOException Thrown if IO errors occurred.
   */
  public static POSModel train(String languageCode, ObjectStream<POSSample> samples,
                               TrainingParameters mlParams, POSTaggerFactory posFactory)
      throws IOException {

    final int beamSize = mlParams.getIntParameter(
            BeamSearch.BEAM_SIZE_PARAMETER, POSTaggerME.DEFAULT_BEAM_SIZE);

    final POSContextGenerator contextGenerator = posFactory.getPOSContextGenerator();
    final TrainerType trainerType = TrainerFactory.getTrainerType(mlParams);
    final Map<String, String> manifestInfoEntries = new HashMap<>();

    MaxentModel posModel = null;
    SequenceClassificationModel seqPosModel = null;
    if (TrainerType.EVENT_MODEL_TRAINER.equals(trainerType)) {
      ObjectStream<Event> es = new POSSampleEventStream(samples, contextGenerator);

      EventTrainer<TrainingParameters> trainer =
          TrainerFactory.getEventTrainer(mlParams, manifestInfoEntries);
      posModel = trainer.train(es);
    } else if (TrainerType.EVENT_MODEL_SEQUENCE_TRAINER.equals(trainerType)) {
      POSSampleSequenceStream ss = new POSSampleSequenceStream(samples, contextGenerator);
      EventModelSequenceTrainer<POSSample, TrainingParameters> trainer =
          TrainerFactory.getEventModelSequenceTrainer(mlParams, manifestInfoEntries);
      posModel = trainer.train(ss);
    } else if (TrainerType.SEQUENCE_TRAINER.equals(trainerType)) {
      SequenceTrainer<TrainingParameters> trainer = TrainerFactory.getSequenceModelTrainer(
          mlParams, manifestInfoEntries);

      // TODO: This will probably cause issues, since the feature generator uses the outcomes array

      POSSampleSequenceStream ss = new POSSampleSequenceStream(samples, contextGenerator);
      seqPosModel = trainer.train(ss);
    } else {
      throw new IllegalArgumentException("Trainer type is not supported: " + trainerType);
    }

    if (posModel != null) {
      return new POSModel(languageCode, posModel, beamSize, manifestInfoEntries, posFactory);
    } else {
      return new POSModel(languageCode, seqPosModel, manifestInfoEntries, posFactory);
    }
  }

  /**
   * Constructs an {@link Dictionary nGram dictionary} from an {@link ObjectStream} of samples.
   *
   * @param samples The {@link ObjectStream} to process.
   * @param cutoff  A non-negative cut-off value.
   * @return A valid {@link Dictionary} instance holding nGrams.
   * @throws IOException Thrown if IO errors occurred during dictionary construction.
   */
  public static Dictionary buildNGramDictionary(ObjectStream<POSSample> samples, int cutoff)
      throws IOException {

    NGramModel ngramModel = new NGramModel();
    POSSample sample;
    while ((sample = samples.read()) != null) {
      String[] words = sample.getSentence();

      if (words.length > 0) {
        ngramModel.add(new StringList(words), 1, 1);
      }
    }

    ngramModel.cutoff(cutoff, Integer.MAX_VALUE);

    return ngramModel.toDictionary(true);
  }

  /**
   * Populates a {@link POSDictionary} from an {@link ObjectStream} of samples.
   *
   * @param samples The {@link ObjectStream} to process.
   * @param dict    The {@link MutableTagDictionary} to use during population.
   * @param cutoff  A non-negative cut-off value.
   * @throws IOException Thrown if IO errors occurred during dictionary construction.
   */
  public static void populatePOSDictionary(ObjectStream<POSSample> samples,
                                           MutableTagDictionary dict, int cutoff) throws IOException {

    logger.info("Expanding POS Dictionary ...");
    long start = System.nanoTime();

    // the data structure will store the word, the tag, and the number of occurrences
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

    // now we check if the word + tag pairs have enough occurrences,
    // if yes we add it to the dictionary
    for (Entry<String, Map<String, AtomicInteger>> wordEntry : newEntries
        .entrySet()) {
      List<String> tagsForWord = new ArrayList<>();
      for (Entry<String, AtomicInteger> entry : wordEntry.getValue().entrySet()) {
        if (entry.getValue().get() >= cutoff) {
          tagsForWord.add(entry.getKey());
        }
      }
      if (!tagsForWord.isEmpty()) {
        dict.put(wordEntry.getKey(), tagsForWord.toArray(new String[0]));
      }
    }

    logger.info("... finished expanding POS Dictionary. [ {} ms]", (System.nanoTime() - start) / 1000000);
  }
}
