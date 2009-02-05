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


package opennlp.tools.namefind;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import opennlp.maxent.GIS;
import opennlp.maxent.GISModel;
import opennlp.maxent.PlainTextByLineDataStream;
import opennlp.model.AbstractModel;
import opennlp.model.EventStream;
import opennlp.model.MaxentModel;
import opennlp.model.TwoPassDataIndexer;
import opennlp.tools.util.BeamSearch;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ModelUtil;
import opennlp.tools.util.Sequence;
import opennlp.tools.util.SequenceValidator;
import opennlp.tools.util.Span;
import opennlp.tools.util.featuregen.AdaptiveFeatureGenerator;
import opennlp.tools.util.featuregen.AdditionalContextFeatureGenerator;
import opennlp.tools.util.featuregen.Factory;
import opennlp.tools.util.featuregen.WindowFeatureGenerator;

/**
 * Class for creating a maximum-entropy-based name finder.
 */
public class NameFinderME implements TokenNameFinder {

  private static String[][] EMPTY = new String[0][0];

  private static class NameFinderSequenceValidator implements
      SequenceValidator<String> {

    public boolean validSequence(int i, String[] inputSequence,
        String[] outcomesSequence, String outcome) {
      
      if (outcome.equals(CONTINUE)) {
        
        int li = outcomesSequence.length - 1;
        
        if (li == -1) {
          return false;
        } else if (outcomesSequence[li].equals(OTHER)) {
          return false;
        }
      }
      
      return true;
    }
  }

  public static final String START = "start";
  public static final String CONTINUE = "cont";
  public static final String OTHER = "other";

  protected MaxentModel model;
  protected NameContextGenerator contextGenerator;
  private Sequence bestSequence;
  private BeamSearch<String> beam;

  private AdditionalContextFeatureGenerator additionalContextFeatureGenerator =
      new AdditionalContextFeatureGenerator();

  public NameFinderME(TokenNameFinderModel model) {
    this(model, 3);
  }

  /**
   * Initializes the name finder with the specified model.
   *
   * @param model
   * @param beamSize
   */
  public NameFinderME(TokenNameFinderModel model, int beamSize) {
    this.model = model.getNameFinderModel();

    contextGenerator = new DefaultNameContextGenerator(model.createFeatureGenerators());

    contextGenerator.addFeatureGenerator(
          new WindowFeatureGenerator(additionalContextFeatureGenerator, 8, 8));
    
    beam = new BeamSearch<String>(beamSize, contextGenerator, this.model,
        new NameFinderSequenceValidator(), beamSize);
  }

  /**
   * Creates a new name finder with the specified model.
   * 
   * @param mod The model to be used to find names.
   */
  @Deprecated
  public NameFinderME(MaxentModel mod) {
    this(mod, new DefaultNameContextGenerator(), 3);
  }

  /**
   * Creates a new name finder with the specified model and context generator.
   * 
   * @param mod The model to be used to find names.
   * @param cg The context generator to be used with this name finder.
   */
  @Deprecated
  public NameFinderME(MaxentModel mod, NameContextGenerator cg) {
    this(mod, cg, 3);
  }

  /**
   * Creates a new name finder with the specified model and context generator.
   * 
   * @param mod The model to be used to find names.
   * @param cg The context generator to be used with this name finder.
   * @param beamSize The size of the beam to be used in decoding this model.
   */
  @Deprecated
  public NameFinderME(MaxentModel mod, NameContextGenerator cg, int beamSize) {
    model = mod;
    contextGenerator = cg;

    contextGenerator.addFeatureGenerator(new WindowFeatureGenerator(additionalContextFeatureGenerator, 8, 8));
    beam = new BeamSearch<String>(beamSize, cg, mod,
        new NameFinderSequenceValidator(), beamSize);
  }

  public Span[] find(String[] tokens) {
    return find(tokens, EMPTY);
  }

  /** 
   * Generates name tags for the given sequence, typically a sentence, returning token spans for any identified names.
   * 
   * @param tokens an array of the tokens or words of the sequence, typically a sentence.
   * @param additionalContext features which are based on context outside of the sentence but which should also be used.
   * 
   * @return an array of spans for each of the names identified.
   */
  public Span[] find(String[] tokens, String[][] additionalContext) {
    additionalContextFeatureGenerator.setCurrentContext(additionalContext);
    bestSequence = beam.bestSequence(tokens, additionalContext);
    List<String> c = bestSequence.getOutcomes();

    contextGenerator.updateAdaptiveData(tokens, (String[]) c.toArray(new String[c.size()]));

    int start = -1;
    int end = -1;
    List<Span> spans = new ArrayList<Span>(tokens.length);
    for (int li = 0; li < c.size(); li++) {
      String chunkTag = (String) c.get(li);
      if (chunkTag.equals(NameFinderME.START)) {
        if (start != -1) {
          spans.add(new Span(start, end));
        }

        start = li;
        end = li + 1;

      }
      else if (chunkTag.equals(NameFinderME.CONTINUE)) {
        end = li + 1;
      }
      else if (chunkTag.equals(NameFinderME.OTHER)) {
        if (start != -1) {
          spans.add(new Span(start, end));
          start = -1;
          end = -1;
        }
      }
    }

    if (start != -1) {
      spans.add(new Span(start,end));
    }

    return spans.toArray(new Span[spans.size()]);
  }

  /**
   * Forgets all adaptive data which was collected during previous
   * calls to one of the find methods.
   *
   * This method is typical called at the end of a document.
   */
  public void clearAdaptiveData() {
   contextGenerator.clearAdaptiveData();
  }

  /**
   * Populates the specified array with the probabilities of the last decoded
   * sequence. The sequence was determined based on the previous call to
   * <code>chunk</code>. The specified array should be at least as large as
   * the number of tokens in the previous call to <code>chunk</code>.
   *
   * @param probs
   *          An array used to hold the probabilities of the last decoded
   *          sequence.
   */
   public void probs(double[] probs) {
     bestSequence.getProbs(probs);
   }

  /**
    * Returns an array with the probabilities of the last decoded sequence.  The
    * sequence was determined based on the previous call to <code>chunk</code>.
    * 
    * @return An array with the same number of probabilities as tokens were sent to <code>chunk</code>
    * when it was last called.
    */
   public double[] probs() {
     return bestSequence.getProbs();
   }

   /**
    * Returns an array of probabilities for each of the specified spans which is the product
    * the probabilities for each of the outcomes which make up the span.
    * 
    * @param spans The spans of the names for which probabilities are desired.
    * 
    * @return an array of probabilities for each of the specified spans.
    */
   public double[] probs(Span[] spans) {
     
     double[] sprobs = new double[spans.length];
     double[] probs = bestSequence.getProbs();
     
     for (int si=0;si<spans.length;si++) {
       
       double p = 1;
       
       for (int oi = spans[si].getStart(); oi < spans[si].getEnd(); oi++) {
         p *= probs[oi];
       }
       
       sprobs[si] = p;
     }
     
     return sprobs;
   }

   public static TokenNameFinderModel train(String languageCode, Iterator<NameSample> samples, InputStream descriptorIn,
       Map<String, Object> resources) throws IOException, InvalidFormatException {

     byte descriptorBytes[] = ModelUtil.read(descriptorIn);
     
     // TODO: create resource manager
     AdaptiveFeatureGenerator generator = Factory.create(new ByteArrayInputStream(descriptorBytes), null);

     EventStream eventStream = new NameFinderEventStream(samples,
         new DefaultNameContextGenerator(generator));

     AbstractModel nameFinderModel = GIS.trainModel(100, new TwoPassDataIndexer(eventStream, 5));

     return new TokenNameFinderModel(languageCode, nameFinderModel,
         new ByteArrayInputStream(descriptorBytes), resources);
   }

  @Deprecated
  public static GISModel train(EventStream es, int iterations, int cut) throws IOException {
    return GIS.trainModel(iterations, new TwoPassDataIndexer(es, cut));
  }

  /**
   * Trains a new named entity model on the specified training file using the specified encoding to read it in.
   * 
   * @param args [-encoding encoding] training_file model_file
   * 
   * @throws java.io.IOException
   */
  public static void main(String[] args) throws IOException, InvalidFormatException {
    
    // Encoding must be specified !!!
    // -encoding code train.file featuregen.file model.file
    
    if (args.length == 5) {
      
      NameSampleDataStream sampleStream = new NameSampleDataStream(
          new PlainTextByLineDataStream(new InputStreamReader(new FileInputStream(args[2]), args[1])));
      
      InputStream descriptorIn = new FileInputStream(args[3]);
      
      TokenNameFinderModel model = 
          NameFinderME.train("x-unspecified", sampleStream, descriptorIn, new HashMap<String, Object>());
      
      model.serialize(new FileOutputStream(args[4]));
      
    }
    else {
      // TODO: Usage
    }
  }
}
