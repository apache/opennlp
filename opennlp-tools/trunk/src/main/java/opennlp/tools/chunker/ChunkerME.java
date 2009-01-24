/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.List;

import opennlp.model.AbstractModel;
import opennlp.model.MaxentModel;
import opennlp.model.TwoPassDataIndexer;
import opennlp.tools.util.BeamSearch;
import opennlp.tools.util.Sequence;

/**
 * The class represents a maximum-entropy-based chunker.  Such a chunker can be used to
 * find flat structures based on sequence inputs such as noun phrases or named entities.
 */
public class ChunkerME implements Chunker {

  private static final int DEFAULT_BEAM_SIZE = 10;

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
   * Initializes the current instance with the specified model.
   * The default beam size is used.
   *
   * @param model
   */
  public ChunkerME(ChunkerModel model) {
    this(model, DEFAULT_BEAM_SIZE);
  }

  /**
   * Initializes the current instance with the specified model and
   * the specified beam size.
   *
   * @param model
   * @param beamSize
   */
  public ChunkerME(ChunkerModel model, int beamSize) {
    this.model = model.getChunkerModel();
    beam = new ChunkBeamSearch(beamSize, new DefaultChunkerContextGenerator(), this.model);
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
    beam = new ChunkBeamSearch(beamSize, cg, mod);
    this.model = mod;
  }

  /* inherieted javadoc */
  public List<String> chunk(List<String> toks, List<String> tags) {
    bestSequence =
        beam.bestSequence(toks.toArray(new String[toks.size()]), new Object[] { (String[]) tags.toArray(new String[tags.size()]) });
    return bestSequence.getOutcomes();
  }

  /* inherieted javadoc */
  public String[] chunk(String[] toks, String[] tags) {
    bestSequence = beam.bestSequence(toks, new Object[] {tags});
    List<String> c = bestSequence.getOutcomes();
    return c.toArray(new String[c.size()]);
  }

  public Sequence[] topKSequences(List<String> sentence, List<String> tags) {
    return beam.bestSequences(DEFAULT_BEAM_SIZE, sentence.toArray(new String[sentence.size()]), new Object[] { tags });
  }

  public Sequence[] topKSequences(String[] sentence, String[] tags, double minSequenceScore) {
    return beam.bestSequences(DEFAULT_BEAM_SIZE, sentence, new Object[] { tags },minSequenceScore);
  }

  /**
    * This method determines whether the outcome is valid for the preceding sequence.
    * This can be used to implement constraints on what sequences are valid.
    *
    * @param outcome The outcome.
    * @param sequence The preceding sequence of outcome assignments.
    * @return true is the outcome is valid for the sequence, false otherwise.
    *
    */
  protected boolean validOutcome(String outcome, String[] sequence) {
    return true;
  }

  /**
   * This class implements the abstract BeamSearch class to allow for the chunker to use
   * the common beam search code.
   *
   */
  class ChunkBeamSearch extends BeamSearch<String> {

    ChunkBeamSearch(int size, ChunkerContextGenerator cg, MaxentModel model) {
      super(size, cg, model);
    }

    protected boolean validSequence(int i, String[] sequence, String[] s, String outcome) {
      return validOutcome(outcome, s);
    }
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

  /**
   * Trains a new model for the {@link ChunkerME}.
   *
   * @param es
   * @param iterations
   * @param cut
   * @return the new model
   * @throws java.io.IOException
   */
  public static AbstractModel train(opennlp.model.EventStream es, int iterations, int cut) throws java.io.IOException {
    return opennlp.maxent.GIS.trainModel(iterations, new TwoPassDataIndexer(es, cut));
  }

  private static void usage() {
    System.err.println("Usage: ChunkerME [-encoding charset] trainingFile modelFile");
    System.err.println();
    System.err.println("Training file should be one word per line where each line consists of a ");
    System.err.println("space-delimited triple of \"word pos outcome\".  Sentence breaks are indicated by blank lines.");
    System.exit(1);
  }

  /**
   * Trains the chunker using the specified parameters. <br>
   * Usage: ChunkerME trainingFile modelFile. <br>
   * Training file should be one word per line where each line consists of a
   * space-delimited triple of "word pos outcome".  Sentence breaks are indicated by blank lines.
   * @param args The training file and the model file.
   * @throws java.io.IOException When the specifed files can not be read.
   */
  public static void main(String[] args) throws java.io.IOException {
    if (args.length == 0) {
      usage();
    }
    int ai = 0;
    String encoding = null;
    while (args[ai].startsWith("-")) {
      if (args[ai].equals("-encoding") && ai+1 < args.length) {
        ai++;
        encoding = args[ai];
      }
      else {
        System.err.println("Unknown option: "+args[ai]);
        usage();
      }
      ai++;
    }
    java.io.File inFile = null;
    java.io.File outFile = null;
    if (ai < args.length) {
      inFile = new java.io.File(args[ai++]);
    }
    else {
      usage();
    }
    if (ai < args.length) {
      outFile = new java.io.File(args[ai++]);
    }
    else {
      usage();
    }
    int iterations = 100;
    int cutoff = 5;
    if (args.length > ai) {
      iterations = Integer.parseInt(args[ai++]);
    }
    if (args.length > ai) {
      cutoff = Integer.parseInt(args[ai++]);
    }
    AbstractModel mod;
    opennlp.model.EventStream es;
    if (encoding != null) {
       es = new ChunkerEventStream(new opennlp.maxent.PlainTextByLineDataStream(new InputStreamReader(new FileInputStream(inFile),encoding)));
    }
    else {
      es = new ChunkerEventStream(new opennlp.maxent.PlainTextByLineDataStream(new java.io.FileReader(inFile)));
    }
    mod = train(es, iterations, cutoff);
    System.out.println("Saving the model as: " + args[1]);
    new opennlp.maxent.io.SuffixSensitiveGISModelWriter(mod, outFile).persist();
  }
}
