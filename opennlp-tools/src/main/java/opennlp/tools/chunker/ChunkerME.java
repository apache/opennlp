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
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Sequence;
import opennlp.tools.util.SequenceValidator;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;

/**
 * The class represents a maximum-entropy-based chunker.  Such a chunker can be used to
 * find flat structures based on sequence inputs such as noun phrases or named entities.
 */
public class ChunkerME implements Chunker {

  public static final int DEFAULT_BEAM_SIZE = 10;

  private Sequence bestSequence;

  /**
   * The model used to assign chunk tags to a sequence of tokens.
   */
  protected SequenceClassificationModel<String> model;

  private ChunkerContextGenerator contextGenerator;
  private SequenceValidator<String> sequenceValidator;

  /**
   * Initializes the current instance with the specified model and
   * the specified beam size.
   *
   * @param model The model for this chunker.
   * @param beamSize The size of the beam that should be used when decoding sequences.
   * @param sequenceValidator  The {@link SequenceValidator} to determines whether the outcome
   *        is valid for the preceding sequence. This can be used to implement constraints
   *        on what sequences are valid.
   * @deprecated Use {@link #ChunkerME(ChunkerModel, int)} instead and use the {@link ChunkerFactory}
   *     to configure the {@link SequenceValidator} and {@link ChunkerContextGenerator}.
   */
  @Deprecated
  private ChunkerME(ChunkerModel model, int beamSize, SequenceValidator<String> sequenceValidator,
      ChunkerContextGenerator contextGenerator) {

    this.sequenceValidator = sequenceValidator;
    this.contextGenerator = contextGenerator;

    if (model.getChunkerSequenceModel() != null) {
      this.model = model.getChunkerSequenceModel();
    }
    else {
      this.model = new opennlp.tools.ml.BeamSearch<>(beamSize,
          model.getChunkerModel(), 0);
    }
  }

  /**
   * Initializes the current instance with the specified model and
   * the specified beam size.
   *
   * @param model The model for this chunker.
   * @param beamSize The size of the beam that should be used when decoding sequences.
   *
   * @deprecated beam size is now stored inside the model
   */
  @Deprecated
  private ChunkerME(ChunkerModel model, int beamSize) {

    contextGenerator = model.getFactory().getContextGenerator();
    sequenceValidator = model.getFactory().getSequenceValidator();

    if (model.getChunkerSequenceModel() != null) {
      this.model = model.getChunkerSequenceModel();
    }
    else {
      this.model = new opennlp.tools.ml.BeamSearch<>(beamSize,
          model.getChunkerModel(), 0);
    }
  }

  /**
   * Initializes the current instance with the specified model.
   * The default beam size is used.
   *
   * @param model
   */
  public ChunkerME(ChunkerModel model) {
    this(model, DEFAULT_BEAM_SIZE);
  }

  public String[] chunk(String[] toks, String[] tags) {
    bestSequence = model.bestSequence(toks, new Object[] {tags}, contextGenerator, sequenceValidator);
    List<String> c = bestSequence.getOutcomes();
    return c.toArray(new String[c.size()]);
  }

  public Span[] chunkAsSpans(String[] toks, String[] tags) {
    String[] preds = chunk(toks, tags);
    return ChunkSample.phrasesAsSpanList(toks, tags, preds);
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
   * sequence was determined based on the previous call to <code>chunk</code>.  The
   * specified array should be at least as large as the numbe of tokens in the previous
   * call to <code>chunk</code>.
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

  public static ChunkerModel train(String lang, ObjectStream<ChunkSample> in,
      TrainingParameters mlParams, ChunkerFactory factory) throws IOException {

    String beamSizeString = mlParams.getSettings().get(BeamSearch.BEAM_SIZE_PARAMETER);

    int beamSize = ChunkerME.DEFAULT_BEAM_SIZE;
    if (beamSizeString != null) {
      beamSize = Integer.parseInt(beamSizeString);
    }

    Map<String, String> manifestInfoEntries = new HashMap<>();

    TrainerType trainerType = TrainerFactory.getTrainerType(mlParams.getSettings());


    MaxentModel chunkerModel = null;
    SequenceClassificationModel<String> seqChunkerModel = null;

    if (TrainerType.EVENT_MODEL_TRAINER.equals(trainerType)) {
      ObjectStream<Event> es = new ChunkerEventStream(in, factory.getContextGenerator());
      EventTrainer trainer = TrainerFactory.getEventTrainer(mlParams.getSettings(),
          manifestInfoEntries);
      chunkerModel = trainer.train(es);
    }
    else if (TrainerType.SEQUENCE_TRAINER.equals(trainerType)) {
      SequenceTrainer trainer = TrainerFactory.getSequenceModelTrainer(
          mlParams.getSettings(), manifestInfoEntries);

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
