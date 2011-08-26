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


package opennlp.tools.namefind;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import opennlp.maxent.GIS;
import opennlp.maxent.GISModel;
import opennlp.model.AbstractModel;
import opennlp.model.EventStream;
import opennlp.model.MaxentModel;
import opennlp.model.TrainUtil;
import opennlp.model.TwoPassDataIndexer;
import opennlp.tools.util.BeamSearch;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Sequence;
import opennlp.tools.util.SequenceValidator;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.featuregen.AdaptiveFeatureGenerator;
import opennlp.tools.util.featuregen.AdditionalContextFeatureGenerator;
import opennlp.tools.util.featuregen.BigramNameFeatureGenerator;
import opennlp.tools.util.featuregen.CachedFeatureGenerator;
import opennlp.tools.util.featuregen.FeatureGeneratorResourceProvider;
import opennlp.tools.util.featuregen.GeneratorFactory;
import opennlp.tools.util.featuregen.OutcomePriorFeatureGenerator;
import opennlp.tools.util.featuregen.PreviousMapFeatureGenerator;
import opennlp.tools.util.featuregen.SentenceFeatureGenerator;
import opennlp.tools.util.featuregen.TokenClassFeatureGenerator;
import opennlp.tools.util.featuregen.TokenFeatureGenerator;
import opennlp.tools.util.featuregen.WindowFeatureGenerator;
import opennlp.tools.util.model.ModelUtil;

/**
 * Class for creating a maximum-entropy-based name finder.
 */
public class NameFinderME implements TokenNameFinder {

  private static String[][] EMPTY = new String[0][0];
  public static final int DEFAULT_BEAM_SIZE = 3;
  private static final Pattern typedOutcomePattern = Pattern.compile("(.+)-\\w+");



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
    this(model, DEFAULT_BEAM_SIZE);
  }

  /**
   * Initializes the name finder with the specified model.
   *
   * @param model
   * @param beamSize
   */
  public NameFinderME(TokenNameFinderModel model, AdaptiveFeatureGenerator generator, int beamSize,
      SequenceValidator<String> sequenceValidator) {
    this.model = model.getNameFinderModel();
    
    // If generator is provided always use that one
    if (generator != null) {
      contextGenerator = new DefaultNameContextGenerator(generator);
    }
    else {
      // If model has a generator use that one, otherwise create default 
      AdaptiveFeatureGenerator featureGenerator = model.createFeatureGenerators();
      
      if (featureGenerator == null)
        featureGenerator = createFeatureGenerator();
      
      contextGenerator = new DefaultNameContextGenerator(featureGenerator);
    }
    
    contextGenerator.addFeatureGenerator(
          new WindowFeatureGenerator(additionalContextFeatureGenerator, 8, 8));
    
    if (sequenceValidator == null)
      sequenceValidator = new NameFinderSequenceValidator();
    
    beam = new BeamSearch<String>(beamSize, contextGenerator, this.model,
        sequenceValidator, beamSize);
  }

  public NameFinderME(TokenNameFinderModel model, AdaptiveFeatureGenerator generator, int beamSize) {
    this(model, generator, beamSize, null);
  }
  
  public NameFinderME(TokenNameFinderModel model, int beamSize) {
    this(model, null, beamSize);
  }
  
  
  /**
   * Creates a new name finder with the specified model.
   * 
   * @param mod The model to be used to find names.
   * 
   * @deprecated Use the new model API! 
   */
  @Deprecated
  public NameFinderME(MaxentModel mod) {
    this(mod, new DefaultNameContextGenerator(), DEFAULT_BEAM_SIZE);
  }

  /**
   * Creates a new name finder with the specified model and context generator.
   * 
   * @param mod The model to be used to find names.
   * @param cg The context generator to be used with this name finder.
   */
  @Deprecated
  public NameFinderME(MaxentModel mod, NameContextGenerator cg) {
    this(mod, cg, DEFAULT_BEAM_SIZE);
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

  private static AdaptiveFeatureGenerator createFeatureGenerator() {
   return new CachedFeatureGenerator(
         new AdaptiveFeatureGenerator[]{
           new WindowFeatureGenerator(new TokenFeatureGenerator(), 2, 2),
           new WindowFeatureGenerator(new TokenClassFeatureGenerator(true), 2, 2),
           new OutcomePriorFeatureGenerator(),
           new PreviousMapFeatureGenerator(),
           new BigramNameFeatureGenerator(),
           new SentenceFeatureGenerator(true, false)
           });
  }
  
  private static AdaptiveFeatureGenerator createFeatureGenerator(
      byte[] generatorDescriptor, final Map<String, Object> resources)
      throws IOException {
    AdaptiveFeatureGenerator featureGenerator;

    if (generatorDescriptor != null) {
      featureGenerator = GeneratorFactory.create(new ByteArrayInputStream(
          generatorDescriptor), new FeatureGeneratorResourceProvider() {

        public Object getResource(String key) {
          return resources.get(key);
        }
      });
    } else {
      featureGenerator = null;
    }

    return featureGenerator;
  }
  
  public Span[] find(String[] tokens) {
    return find(tokens, EMPTY);
  }
  
  /** 
   * Generates name tags for the given sequence, typically a sentence, 
   * returning token spans for any identified names.
   * 
   * @param tokens an array of the tokens or words of the sequence,
   *     typically a sentence.
   * @param additionalContext features which are based on context outside
   *     of the sentence but which should also be used.
   * 
   * @return an array of spans for each of the names identified.
   */
  public Span[] find(String[] tokens, String[][] additionalContext) {
    additionalContextFeatureGenerator.setCurrentContext(additionalContext);
    bestSequence = beam.bestSequence(tokens, additionalContext);
    
    List<String> c = bestSequence.getOutcomes();

    contextGenerator.updateAdaptiveData(tokens, c.toArray(new String[c.size()]));

    int start = -1;
    int end = -1;
    List<Span> spans = new ArrayList<Span>(tokens.length);
    for (int li = 0; li < c.size(); li++) {
      String chunkTag = c.get(li);
      if (chunkTag.endsWith(NameFinderME.START)) {
        if (start != -1) {
          spans.add(new Span(start, end, extractNameType(chunkTag)));
        }

        start = li;
        end = li + 1;

      }
      else if (chunkTag.endsWith(NameFinderME.CONTINUE)) {
        end = li + 1;
      }
      else if (chunkTag.endsWith(NameFinderME.OTHER)) {
        if (start != -1) {
          spans.add(new Span(start, end, extractNameType(c.get(li - 1))));
          start = -1;
          end = -1;
        }
      }
    }

    if (start != -1) {
      spans.add(new Span(start, end, extractNameType(c.get(c.size() - 1))));
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
    * Returns an array of probabilities for each of the specified spans which is the arithmetic mean 
    * of the probabilities for each of the outcomes which make up the span.
    * 
    * @param spans The spans of the names for which probabilities are desired.
    * 
    * @return an array of probabilities for each of the specified spans.
    */
   public double[] probs(Span[] spans) {
     
     double[] sprobs = new double[spans.length];
     double[] probs = bestSequence.getProbs();
     
     for (int si=0; si<spans.length; si++) {
       
       double p = 0;
       
       for (int oi = spans[si].getStart(); oi < spans[si].getEnd(); oi++) {
         p += probs[oi];
       }
       
       p /= spans[si].length(); 
       
       sprobs[si] = p;
     }
     
     return sprobs;
   }

   /**
    * Trains a name finder model.
    * 
    * @param languageCode
    *          the language of the training data
    * @param type
    *          null or an override type for all types in the training data
    * @param samples
    *          the training data
    * @param trainParams
    *          machine learning train parameters
    * @param generator
    *          null or the feature generator
    * @param resources
    *          the resources for the name finder or null if none
    * 
    * @return the newly trained model
    * 
    * @throws IOException
    */
   public static TokenNameFinderModel train(String languageCode, String type, ObjectStream<NameSample> samples, 
       TrainingParameters trainParams, AdaptiveFeatureGenerator generator, final Map<String, Object> resources) throws IOException {
     
     Map<String, String> manifestInfoEntries = new HashMap<String, String>();
     
     AdaptiveFeatureGenerator featureGenerator;
     
     if (generator != null)
       featureGenerator = generator;
     else 
       featureGenerator = createFeatureGenerator();
     
     AbstractModel nameFinderModel;
     
     if (!TrainUtil.isSequenceTraining(trainParams.getSettings())) {
       EventStream eventStream = new NameFinderEventStream(samples, type,
           new DefaultNameContextGenerator(featureGenerator));
       
       nameFinderModel = TrainUtil.train(eventStream, trainParams.getSettings(), manifestInfoEntries);
     }
     else {
       NameSampleSequenceStream ss = new NameSampleSequenceStream(samples, featureGenerator);

       nameFinderModel = TrainUtil.train(ss, trainParams.getSettings(), manifestInfoEntries);
     }
     
     return new TokenNameFinderModel(languageCode, nameFinderModel,
         resources, manifestInfoEntries);
   }
   
  /**
   * Trains a name finder model.
   * 
   * @param languageCode
   *          the language of the training data
   * @param type
   *          null or an override type for all types in the training data
   * @param samples
   *          the training data
   * @param trainParams
   *          machine learning train parameters
   * @param featureGeneratorBytes
   *          descriptor to configure the feature generation or null
   * @param resources
   *          the resources for the name finder or null if none
   * 
   * @return the newly trained model
   * 
   * @throws IOException
   */
  public static TokenNameFinderModel train(String languageCode, String type,
      ObjectStream<NameSample> samples, TrainingParameters trainParams,
      byte[] featureGeneratorBytes, final Map<String, Object> resources)
      throws IOException {
    
    TokenNameFinderModel model = train(languageCode, type, samples, trainParams,
        createFeatureGenerator(featureGeneratorBytes, resources), resources);
    
    // place the descriptor in the model
    if (featureGeneratorBytes != null) {
      model = model.updateFeatureGenerator(featureGeneratorBytes);
    }
    
    return model;
  }
   
   /**
    * Trains a name finder model.
    * 
    * @param languageCode the language of the training data
    * @param type null or an override type for all types in the training data
    * @param samples the training data
    * @param iterations the number of iterations
    * @param cutoff
    * @param resources the resources for the name finder or null if none
    * 
    * @return the newly trained model
    * 
    * @throws IOException
    * @throws ObjectStreamException
    */
   public static TokenNameFinderModel train(String languageCode, String type, ObjectStream<NameSample> samples, 
       AdaptiveFeatureGenerator generator, final Map<String, Object> resources, 
       int iterations, int cutoff) throws IOException {
     return train(languageCode, type, samples, ModelUtil.createTrainingParameters(iterations, cutoff),
         generator, resources);
   }

   /**
   * @deprecated use {@link #train(String, String, ObjectStream, TrainingParameters, AdaptiveFeatureGenerator, Map)}
   * instead and pass in a TrainingParameters object.
   */
  @Deprecated
   public static TokenNameFinderModel train(String languageCode, String type, ObjectStream<NameSample> samples, 
       final Map<String, Object> resources, int iterations, int cutoff) throws IOException  {
     return train(languageCode, type, samples, (AdaptiveFeatureGenerator) null, resources, iterations, cutoff);
   }
   
   public static TokenNameFinderModel train(String languageCode, String type, ObjectStream<NameSample> samples,
       final Map<String, Object> resources) throws IOException {
     return NameFinderME.train(languageCode, type, samples, resources, 100, 5);
   }
  
   /**
   * @deprecated use {@link #train(String, String, ObjectStream, TrainingParameters, byte[], Map)}
   * instead and pass in a TrainingParameters object.
   */
  @Deprecated
   public static TokenNameFinderModel train(String languageCode, String type, ObjectStream<NameSample> samples, 
       byte[] generatorDescriptor, final Map<String, Object> resources, 
       int iterations, int cutoff) throws IOException {
     
     // TODO: Pass in resource manager ...
     
     AdaptiveFeatureGenerator featureGenerator = createFeatureGenerator(generatorDescriptor, resources);
     
     TokenNameFinderModel model = train(languageCode, type, samples, featureGenerator,
         resources, iterations, cutoff);
     
     if (generatorDescriptor != null) {
       model = model.updateFeatureGenerator(generatorDescriptor);
     }
     
     return model;
   }
   
  @Deprecated
  public static GISModel train(EventStream es, int iterations, int cut) throws IOException {
    return GIS.trainModel(iterations, new TwoPassDataIndexer(es, cut));
  }
  
  /**
   * Gets the name type from the outcome 
   * @param outcome the outcome
   * @return the name type, or null if not set
   */
  static final String extractNameType(String outcome) {
    Matcher matcher = typedOutcomePattern.matcher(outcome);
    if(matcher.matches()) {
      String nameType = matcher.group(1);
      return nameType;
    }
    
    return null;
  }

  /**
   * Removes spans with are intersecting or crossing in anyway.
   * 
   * <p>
   * The following rules are used to remove the spans:<br>
   * Identical spans: The first span in the array after sorting it remains<br>
   * Intersecting spans: The first span after sorting remains<br>
   * Contained spans: All spans which are contained by another are removed<br>
   * 
   * @param spans
   * 
   * @return non-overlapping spans
   */
  public static Span[] dropOverlappingSpans(Span spans[]) {
    
    List<Span> sortedSpans = new ArrayList<Span>(spans.length);
    Collections.addAll(sortedSpans, spans);
    Collections.sort(sortedSpans);
    
    Iterator<Span> it = sortedSpans.iterator();
    
    
    Span lastSpan = null;
    
    while (it.hasNext()) {
      Span span = it.next();
      
      if (lastSpan != null) {
        if (lastSpan.intersects(span)) {
          it.remove();
          span = lastSpan;
        }
      }
      
      lastSpan = span;
    }
    
    return sortedSpans.toArray(new Span[sortedSpans.size()]);
  }
}
