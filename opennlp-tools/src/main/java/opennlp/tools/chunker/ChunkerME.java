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

package opennlp.tools.chunker;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.ml.BeamSearch;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.SequenceTrainer;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.ml.TrainerFactory.TrainerType;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.SequenceClassificationModel;
import opennlp.tools.util.DownloadUtil;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Sequence;
import opennlp.tools.util.SequenceValidator;
import opennlp.tools.util.Span;
import opennlp.tools.util.TokenTag;
import opennlp.tools.util.TrainingParameters;

/**
 * The class represents a maximum-entropy-based {@link Chunker}. This chunker can be used to
 * find flat structures based on sequence inputs such as noun phrases or named entities.
 */
public class ChunkerME implements Chunker {

  public static final int DEFAULT_BEAM_SIZE = 10;

  private Sequence bestSequence;

  /**
   * The model used to assign chunk tags to a sequence of tokens.
   */
  private final SequenceClassificationModel<TokenTag> model;

  private final ChunkerContextGenerator contextGenerator;
  private final SequenceValidator<TokenTag> sequenceValidator;

  /**
   * Initializes a {@link Chunker} by downloading a default model.
   *
   * @param language The language of the model.
   * @throws IOException Thrown if the model cannot be downloaded or saved.
   */
  public ChunkerME(String language) throws IOException {
    this(DownloadUtil.downloadModel(language, DownloadUtil.ModelType.CHUNKER, ChunkerModel.class));
  }

  /**
   * Initializes a {@link Chunker} with the specified {@link ChunkerModel}.
   * The {@link #DEFAULT_BEAM_SIZE} is used.
   *
   * @param model A valid {@link ChunkerModel model} instance.
   */
  public ChunkerME(ChunkerModel model) {
    contextGenerator = model.getFactory().getContextGenerator();
    sequenceValidator = model.getFactory().getSequenceValidator();

    if (model.getChunkerSequenceModel() != null) {
      this.model = model.getChunkerSequenceModel();
    }
    else {
      this.model = new BeamSearch<>(DEFAULT_BEAM_SIZE, model.getChunkerModel(), 0);
    }
  }

  @Override
  public String[] chunk(String[] toks, String[] tags) {
    TokenTag[] tuples = TokenTag.create(toks, tags);
    bestSequence = model.bestSequence(tuples, new Object[] {}, contextGenerator, sequenceValidator);
    List<String> c = bestSequence.getOutcomes();
    return c.toArray(new String[0]);
  }

  @Override
  public Span[] chunkAsSpans(String[] toks, String[] tags) {
    String[] preds = chunk(toks, tags);
    return ChunkSample.phrasesAsSpanList(toks, tags, preds);
  }

  @Override
  public Sequence[] topKSequences(String[] sentence, String[] tags) {
    TokenTag[] tuples = TokenTag.create(sentence, tags);

    return model.bestSequences(DEFAULT_BEAM_SIZE, tuples,
        new Object[] { }, contextGenerator, sequenceValidator);
  }

  @Override
  public Sequence[] topKSequences(String[] sentence, String[] tags, double minSequenceScore) {
    TokenTag[] tuples = TokenTag.create(sentence, tags);
    return model.bestSequences(DEFAULT_BEAM_SIZE, tuples, new Object[] { }, minSequenceScore,
        contextGenerator, sequenceValidator);
  }

  /**
   * Populates the specified array with the probabilities of the last decoded sequence. The
   * sequence was determined based on the previous call to {@code chunk}. The
   * specified array should be at least as large as the number of tokens in the previous
   * call to {@code chunk}.
   *
   * @param probs An array used to hold the probabilities of the last decoded sequence.
   */
  public void probs(double[] probs) {
    bestSequence.getProbs(probs);
  }

  /**
   * Returns an array with the probabilities of the last decoded sequence. The
   * sequence was determined based on the previous call to {@link #chunk(String[], String[])}.
   *
   * @return An array with the same number of probabilities as tokens when
   *         {@link ChunkerME#chunk(String[], String[])} was last called.
   */
  public double[] probs() {
    return bestSequence.getProbs();
  }

  /**
   * Starts a training of a {@link ChunkerModel} with the given parameters.
   *
   * @param lang The ISO conform language code.
   * @param in The {@link ObjectStream} of {@link ChunkSample} used as input for training.
   * @param mlParams The {@link TrainingParameters} for the context of the training.
   * @param factory The {@link ChunkerFactory} for creating related objects defined via {@code mlParams}.
   *
   * @return A valid, trained {@link ChunkerModel} instance.
   * @throws IOException Thrown if IO errors occurred.
   * @throws IllegalArgumentException Thrown if the specified {@link TrainerType} is not supported.
   */
  public static ChunkerModel train(String lang, ObjectStream<ChunkSample> in,
      TrainingParameters mlParams, ChunkerFactory factory) throws IOException {

    int beamSize = mlParams.getIntParameter(BeamSearch.BEAM_SIZE_PARAMETER, ChunkerME.DEFAULT_BEAM_SIZE);

    Map<String, String> manifestInfoEntries = new HashMap<>();

    TrainerType trainerType = TrainerFactory.getTrainerType(mlParams);
    MaxentModel chunkerModel = null;
    SequenceClassificationModel<String> seqChunkerModel = null;

    if (TrainerType.EVENT_MODEL_TRAINER.equals(trainerType)) {
      ObjectStream<Event> es = new ChunkerEventStream(in, factory.getContextGenerator());
      EventTrainer trainer = TrainerFactory.getEventTrainer(mlParams, manifestInfoEntries);
      chunkerModel = trainer.train(es);
    }
    else if (TrainerType.SEQUENCE_TRAINER.equals(trainerType)) {
      SequenceTrainer trainer = TrainerFactory.getSequenceModelTrainer(
          mlParams, manifestInfoEntries);

      // TODO: This will probably cause issue, since the feature generator uses the outcomes array

      ChunkSampleSequenceStream ss = new ChunkSampleSequenceStream(in, factory.getContextGenerator());
      seqChunkerModel = trainer.train(ss);
    }
    else {
      throw new IllegalArgumentException("Trainer type is not supported: " + trainerType);
    }

    if (chunkerModel != null) {
      return new ChunkerModel(lang, chunkerModel, beamSize, manifestInfoEntries, factory);
    }
    else {
      return new ChunkerModel(lang, seqChunkerModel, manifestInfoEntries, factory);
    }
  }
}
