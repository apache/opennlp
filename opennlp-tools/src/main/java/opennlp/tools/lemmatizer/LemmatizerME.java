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

package opennlp.tools.lemmatizer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.ml.BeamSearch;
import opennlp.tools.ml.EventModelSequenceTrainer;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.SequenceTrainer;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.ml.TrainerFactory.TrainerType;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.SequenceClassificationModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Sequence;
import opennlp.tools.util.SequenceValidator;
import opennlp.tools.util.StringUtil;
import opennlp.tools.util.TrainingParameters;

/**
 * A probabilistic lemmatizer.  Tries to predict the induced permutation class
 * for each word depending on its surrounding context. Based on
 * Grzegorz Chrupała. 2008. Towards a Machine-Learning Architecture
 * for Lexical Functional Grammar Parsing. PhD dissertation, Dublin City University.
 * http://grzegorz.chrupala.me/papers/phd-single.pdf
 */
public class LemmatizerME implements Lemmatizer {

  public static final int LEMMA_NUMBER = 29;
  public static final int DEFAULT_BEAM_SIZE = 3;
  protected int beamSize;
  private Sequence bestSequence;

  private SequenceClassificationModel<String> model;

  private LemmatizerContextGenerator contextGenerator;
  private SequenceValidator<String> sequenceValidator;

  /**
   * Initializes the current instance with the provided model
   * and the default beam size of 3.
   *
   * @param model the model
   */
  public LemmatizerME(LemmatizerModel model) {

    LemmatizerFactory factory = model.getFactory();
    int defaultBeamSize = LemmatizerME.DEFAULT_BEAM_SIZE;
    String beamSizeString = model.getManifestProperty(BeamSearch.BEAM_SIZE_PARAMETER);
    if (beamSizeString != null) {
      defaultBeamSize = Integer.parseInt(beamSizeString);
    }

    contextGenerator = factory.getContextGenerator();
    beamSize = defaultBeamSize;

    sequenceValidator = factory.getSequenceValidator();

    if (model.getLemmatizerSequenceModel() != null) {
      this.model = model.getLemmatizerSequenceModel();
    }
    else {
      this.model = new opennlp.tools.ml.BeamSearch<>(beamSize,
          (MaxentModel) model.getLemmatizerSequenceModel(), 0);
    }
  }

  public String[] lemmatize(String[] toks, String[] tags) {
    String[] ses = predictSES(toks, tags);
    String[] lemmas = decodeLemmas(toks, ses);
    return lemmas;
  }

  @Override public List<List<String>> lemmatize(List<String> toks,
      List<String> tags) {
    String[] tokens = toks.toArray(new String[toks.size()]);
    String[] posTags = tags.toArray(new String[tags.size()]);
    String[][] allLemmas = predictLemmas(LEMMA_NUMBER, tokens, posTags);
    List<List<String>> predictedLemmas = new ArrayList<>();
    for (int i = 0; i < allLemmas.length; i++) {
      predictedLemmas.add(Arrays.asList(allLemmas[i]));
    }
    return predictedLemmas;
  }

  /**
   * Predict Short Edit Script (automatically induced lemma class).
   * @param toks the array of tokens
   * @param tags the array of pos tags
   * @return an array containing the lemma classes
   */
  public String[] predictSES(String[] toks, String[] tags) {
    bestSequence = model.bestSequence(toks, new Object[] {tags}, contextGenerator, sequenceValidator);
    List<String> ses = bestSequence.getOutcomes();
    return ses.toArray(new String[ses.size()]);
  }

  /**
   * Predict all possible lemmas (using a default upper bound).
   * @param numLemmas the default number of lemmas
   * @param toks the tokens
   * @param tags the postags
   * @return a double array containing all posible lemmas for each token and postag pair
   */
  public String[][] predictLemmas(int numLemmas, String[] toks, String[] tags) {
    Sequence[] bestSequences = model.bestSequences(numLemmas, toks, new Object[] {tags},
            contextGenerator, sequenceValidator);
    String[][] allLemmas = new String[bestSequences.length][];
    for (int i = 0; i < allLemmas.length; i++) {
      List<String> ses = bestSequences[i].getOutcomes();
      String[] sesArray = ses.toArray(new String[ses.size()]);
      allLemmas[i] = decodeLemmas(toks,sesArray);
    }
    return allLemmas;
  }

  /**
   * Decodes the lemma from the word and the induced lemma class.
   * @param toks the array of tokens
   * @param preds the predicted lemma classes
   * @return the array of decoded lemmas
   */
  public static String[] decodeLemmas(String[] toks, String[] preds) {
    List<String> lemmas = new ArrayList<>();
    for (int i = 0; i < toks.length; i++) {
      String lemma = StringUtil.decodeShortestEditScript(toks[i].toLowerCase(), preds[i]);
      if (lemma.length() == 0) {
        lemma = "_";
      }
      lemmas.add(lemma);
    }
    return lemmas.toArray(new String[lemmas.size()]);
  }

  public static String[] encodeLemmas(String[] toks, String[] lemmas) {
    List<String> sesList = new ArrayList<>();
    for (int i = 0; i < toks.length; i++) {
      String ses = StringUtil.getShortestEditScript(toks[i], lemmas[i]);
      if (ses.length() == 0) {
        ses = "_";
      }
      sesList.add(ses);
    }
    return sesList.toArray(new String[sesList.size()]);
  }

  public Sequence[] topKSequences(String[] sentence, String[] tags) {
    return model.bestSequences(DEFAULT_BEAM_SIZE, sentence,
        new Object[] { tags }, contextGenerator, sequenceValidator);
  }

  public Sequence[] topKSequences(String[] sentence, String[] tags, double minSequenceScore) {
    return model.bestSequences(DEFAULT_BEAM_SIZE, sentence, new Object[] { tags }, minSequenceScore,
        contextGenerator, sequenceValidator);
  }

  /**
   * Populates the specified array with the probabilities of the last decoded sequence.  The
   * sequence was determined based on the previous call to <code>lemmatize</code>.  The
   * specified array should be at least as large as the number of tokens in the
   * previous call to <code>lemmatize</code>.
   *
   * @param probs An array used to hold the probabilities of the last decoded sequence.
   */
  public void probs(double[] probs) {
    bestSequence.getProbs(probs);
  }

  /**
   * Returns an array with the probabilities of the last decoded sequence.  The
   * sequence was determined based on the previous call to <code>chunk</code>.
   * @return An array with the same number of probabilities as tokens were sent to <code>chunk</code>
   *     when it was last called.
   */
  public double[] probs() {
    return bestSequence.getProbs();
  }

  public static LemmatizerModel train(String languageCode,
      ObjectStream<LemmaSample> samples, TrainingParameters trainParams,
      LemmatizerFactory posFactory) throws IOException {

    int beamSize = trainParams.getIntParameter(BeamSearch.BEAM_SIZE_PARAMETER,
            LemmatizerME.DEFAULT_BEAM_SIZE);

    LemmatizerContextGenerator contextGenerator = posFactory.getContextGenerator();

    Map<String, String> manifestInfoEntries = new HashMap<>();

    TrainerType trainerType = TrainerFactory.getTrainerType(trainParams);

    MaxentModel lemmatizerModel = null;
    SequenceClassificationModel<String> seqLemmatizerModel = null;
    if (TrainerType.EVENT_MODEL_TRAINER.equals(trainerType)) {
      ObjectStream<Event> es = new LemmaSampleEventStream(samples, contextGenerator);

      EventTrainer trainer = TrainerFactory.getEventTrainer(trainParams,
          manifestInfoEntries);
      lemmatizerModel = trainer.train(es);
    }
    else if (TrainerType.EVENT_MODEL_SEQUENCE_TRAINER.equals(trainerType)) {
      LemmaSampleSequenceStream ss = new LemmaSampleSequenceStream(samples, contextGenerator);
      EventModelSequenceTrainer trainer =
          TrainerFactory.getEventModelSequenceTrainer(trainParams, manifestInfoEntries);
      lemmatizerModel = trainer.train(ss);
    }
    else if (TrainerType.SEQUENCE_TRAINER.equals(trainerType)) {
      SequenceTrainer trainer = TrainerFactory.getSequenceModelTrainer(
          trainParams, manifestInfoEntries);

      // TODO: This will probably cause issue, since the feature generator uses the outcomes array

      LemmaSampleSequenceStream ss = new LemmaSampleSequenceStream(samples, contextGenerator);
      seqLemmatizerModel = trainer.train(ss);
    }
    else {
      throw new IllegalArgumentException("Trainer type is not supported: " + trainerType);
    }

    if (lemmatizerModel != null) {
      return new LemmatizerModel(languageCode, lemmatizerModel, beamSize, manifestInfoEntries, posFactory);
    }
    else {
      return new LemmatizerModel(languageCode, seqLemmatizerModel, manifestInfoEntries, posFactory);
    }
  }

  public Sequence[] topKLemmaClasses(String[] sentence, String[] tags) {
    return model.bestSequences(DEFAULT_BEAM_SIZE, sentence,
        new Object[] { tags }, contextGenerator, sequenceValidator);
  }

  public Sequence[] topKLemmaClasses(String[] sentence, String[] tags, double minSequenceScore) {
    return model.bestSequences(DEFAULT_BEAM_SIZE, sentence, new Object[] { tags }, minSequenceScore,
        contextGenerator, sequenceValidator);
  }
}
