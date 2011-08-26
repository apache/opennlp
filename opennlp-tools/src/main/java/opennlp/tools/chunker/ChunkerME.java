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
import java.io.ObjectStreamException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import opennlp.model.AbstractModel;
import opennlp.model.EventStream;
import opennlp.model.MaxentModel;
import opennlp.model.TrainUtil;
import opennlp.tools.util.BeamSearch;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Sequence;
import opennlp.tools.util.SequenceValidator;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.ModelUtil;

/**
 * The class represents a maximum-entropy-based chunker.  Such a chunker can be used to
 * find flat structures based on sequence inputs such as noun phrases or named entities.
 */
public class ChunkerME implements Chunker {

  public static final int DEFAULT_BEAM_SIZE = 10;

  /**
   * The beam used to search for sequences of chunk tag assignments.
   */
  protected BeamSearch<String> beam;

  private Sequence bestSequence;

  /**
   * The model used to assign chunk tags to a sequence of tokens.
   */
  protected MaxentModel model;

  /**
   * Initializes the current instance with the specified model and
   * the specified beam size.
   *
   * @param model The model for this chunker.
   * @param beamSize The size of the beam that should be used when decoding sequences.
   * @param sequenceValidator  The {@link SequenceValidator} to determines whether the outcome 
   *        is valid for the preceding sequence. This can be used to implement constraints 
   *        on what sequences are valid.
   */
  public ChunkerME(ChunkerModel model, int beamSize, SequenceValidator<String> sequenceValidator,
      ChunkerContextGenerator contextGenerator) {
    this.model = model.getChunkerModel();
    beam = new BeamSearch<String>(beamSize, contextGenerator, this.model, sequenceValidator, 0);
  }
  
  /**
   * Initializes the current instance with the specified model and
   * the specified beam size.
   *
   * @param model The model for this chunker.
   * @param beamSize The size of the beam that should be used when decoding sequences.
   * @param sequenceValidator  The {@link SequenceValidator} to determines whether the outcome 
   *        is valid for the preceding sequence. This can be used to implement constraints 
   *        on what sequences are valid.
   */
  public ChunkerME(ChunkerModel model, int beamSize,
      SequenceValidator<String> sequenceValidator) {
    this(model, beamSize, sequenceValidator,
        new DefaultChunkerContextGenerator());
  }

  /**
   * Initializes the current instance with the specified model and
   * the specified beam size.
   *
   * @param model The model for this chunker.
   * @param beamSize The size of the beam that should be used when decoding sequences.
   */
  public ChunkerME(ChunkerModel model, int beamSize) {
    this(model, beamSize, null);
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

  /**
   * Creates a chunker using the specified model.
   *
   * @param mod The maximum entropy model for this chunker.
   */
  @Deprecated
  public ChunkerME(MaxentModel mod) {
    this(mod, new DefaultChunkerContextGenerator(), DEFAULT_BEAM_SIZE);
  }

  /**
   * Creates a chunker using the specified model and context generator.
   *
   * @param mod The maximum entropy model for this chunker.
   * @param cg The context generator to be used by the specified model.
   */
  @Deprecated
  public ChunkerME(MaxentModel mod, ChunkerContextGenerator cg) {
    this(mod, cg, DEFAULT_BEAM_SIZE);
  }

  /**
   * Creates a chunker using the specified model and context generator and decodes the
   * model using a beam search of the specified size.
   *
   * @param mod The maximum entropy model for this chunker.
   * @param cg The context generator to be used by the specified model.
   * @param beamSize The size of the beam that should be used when decoding sequences.
   */
  @Deprecated
  public ChunkerME(MaxentModel mod, ChunkerContextGenerator cg, int beamSize) {
    beam = new BeamSearch<String>(beamSize, cg, mod);
    this.model = mod;
  }

  @Deprecated
  public List<String> chunk(List<String> toks, List<String> tags) {
    bestSequence =
        beam.bestSequence(toks.toArray(new String[toks.size()]), new Object[] { tags.toArray(new String[tags.size()]) });
    return bestSequence.getOutcomes();
  }

  public String[] chunk(String[] toks, String[] tags) {
    bestSequence = beam.bestSequence(toks, new Object[] {tags});
    List<String> c = bestSequence.getOutcomes();
    return c.toArray(new String[c.size()]);
  }
  
  public Span[] chunkAsSpans(String[] toks, String[] tags) {
    String[] preds = chunk(toks, tags);
    return ChunkSample.phrasesAsSpanList(toks, tags, preds);
  }

  @Deprecated
  public Sequence[] topKSequences(List<String> sentence, List<String> tags) {
    return topKSequences(sentence.toArray(new String[sentence.size()]),
        tags.toArray(new String[tags.size()]));
  }
  
  public Sequence[] topKSequences(String[] sentence, String[] tags) {
    return beam.bestSequences(DEFAULT_BEAM_SIZE, sentence,
        new Object[] { tags });
  }

  public Sequence[] topKSequences(String[] sentence, String[] tags, double minSequenceScore) {
    return beam.bestSequences(DEFAULT_BEAM_SIZE, sentence, new Object[] { tags },minSequenceScore);
  }

  /**
   * Populates the specified array with the probabilities of the last decoded sequence.  The
   * sequence was determined based on the previous call to <code>chunk</code>.  The
   * specified array should be at least as large as the numbe of tokens in the previous call to <code>chunk</code>.
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
     * when it was last called.
     */
  public double[] probs() {
    return bestSequence.getProbs();
  }

  public static ChunkerModel train(String lang, ObjectStream<ChunkSample> in, 
      ChunkerContextGenerator contextGenerator, TrainingParameters mlParams)
  throws IOException {
    
    Map<String, String> manifestInfoEntries = new HashMap<String, String>();
    
    EventStream es = new ChunkerEventStream(in, contextGenerator);
    
    AbstractModel maxentModel = TrainUtil.train(es, mlParams.getSettings(), manifestInfoEntries);
    
    return new ChunkerModel(lang, maxentModel, manifestInfoEntries);
  }
  
  /**
   * @deprecated use {@link #train(String, ObjectStream, ChunkerContextGenerator, TrainingParameters)}
   * instead and pass in a TrainingParameters object.
   */
  public static ChunkerModel train(String lang, ObjectStream<ChunkSample> in, 
      int cutoff, int iterations, ChunkerContextGenerator contextGenerator)
      throws IOException {
    return train(lang, in, contextGenerator, ModelUtil.createTrainingParameters(iterations, cutoff));
  }
  
  /**
   * Trains a new model for the {@link ChunkerME}.
   *
   * @param in
   * @param cutoff
   * @param iterations
   * 
   * @return the new model
   * 
   * @throws IOException
   * 
   * @deprecated use {@link #train(String, ObjectStream, ChunkerContextGenerator, TrainingParameters)}
   * instead and pass in a TrainingParameters object.
   */
  @Deprecated
  public static ChunkerModel train(String lang, ObjectStream<ChunkSample> in, int cutoff, int iterations)
      throws IOException, ObjectStreamException {
    return train(lang, in, cutoff, iterations, new DefaultChunkerContextGenerator());
  }
}
