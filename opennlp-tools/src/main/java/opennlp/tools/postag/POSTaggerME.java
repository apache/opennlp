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

import opennlp.model.AbstractModel;
import opennlp.model.EventStream;
import opennlp.model.TrainUtil;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.ngram.NGramModel;
import opennlp.tools.util.BeamSearch;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Sequence;
import opennlp.tools.util.SequenceValidator;
import opennlp.tools.util.StringList;
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
  
  /**
   * The maximum entropy model to use to evaluate contexts.
   */
  protected AbstractModel posModel;

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

  public static final int DEFAULT_BEAM_SIZE = 3;

  /**
   * The size of the beam to be used in determining the best sequence of pos tags.
   */
  protected int size;

  private Sequence bestSequence;

  /**
   * The search object used for search multiple sequences of tags.
   */
  protected BeamSearch<String> beam;

  /**
   * Constructor that overrides the {@link SequenceValidator} from the model.
   * 
   * @deprecated use {@link #POSTaggerME(POSModel, int, int)} instead. The model
   *             knows which {@link SequenceValidator} to use.
   */
  public POSTaggerME(POSModel model, int beamSize, int cacheSize, SequenceValidator<String> sequenceValidator) {
    POSTaggerFactory factory = model.getFactory();
    posModel = model.getPosModel();
    model.getTagDictionary();
    contextGen = factory.getPOSContextGenerator(beamSize);
    tagDictionary = factory.getTagDictionary();
    size = beamSize;
    beam = new BeamSearch<String>(size, contextGen, posModel,
        sequenceValidator, cacheSize);
  }
  
  /**
   * Initializes the current instance with the provided
   * model and provided beam size.
   *
   * @param model
   * @param beamSize
   */
  public POSTaggerME(POSModel model, int beamSize, int cacheSize) {
    POSTaggerFactory factory = model.getFactory();
    posModel = model.getPosModel();
    contextGen = factory.getPOSContextGenerator(beamSize);
    tagDictionary = factory.getTagDictionary();
    size = beamSize;
    beam = new BeamSearch<String>(size, contextGen, posModel,
        factory.getSequenceValidator(), cacheSize);
  }
  
  /**
   * Initializes the current instance with the provided model
   * and the default beam size of 3.
   *
   * @param model
   */
  public POSTaggerME(POSModel model) {
    this(model, DEFAULT_BEAM_SIZE, 0);
  }

  /**
   * Creates a new tagger with the specified model and tag dictionary.
   *
   * @param model The model used for tagging.
   * @param tagdict The tag dictionary used for specifying a set of valid tags.
   */
  @Deprecated
  public POSTaggerME(AbstractModel model, TagDictionary tagdict) {
    this(model, new DefaultPOSContextGenerator(null),tagdict);
  }

  /**
   * Creates a new tagger with the specified model and n-gram dictionary.
   *
   * @param model The model used for tagging.
   * @param dict The n-gram dictionary used for feature generation.
   */
  @Deprecated
  public POSTaggerME(AbstractModel model, Dictionary dict) {
    this(model, new DefaultPOSContextGenerator(dict));
  }

  /**
   * Creates a new tagger with the specified model, n-gram dictionary, and tag dictionary.
   *
   * @param model The model used for tagging.
   * @param dict The n-gram dictionary used for feature generation.
   * @param tagdict The dictionary which specifies the valid set of tags for some words.
   */
  @Deprecated
  public POSTaggerME(AbstractModel model, Dictionary dict, TagDictionary tagdict) {
      this(DEFAULT_BEAM_SIZE,model, new DefaultPOSContextGenerator(dict),tagdict);
    }

  /**
   * Creates a new tagger with the specified model and context generator.
   *
   * @param model The model used for tagging.
   * @param cg The context generator used for feature creation.
   */
  @Deprecated
  public POSTaggerME(AbstractModel model, POSContextGenerator cg) {
    this(DEFAULT_BEAM_SIZE, model, cg, null);
  }

  /**
   * Creates a new tagger with the specified model, context generator, and tag dictionary.
   *
   * @param model The model used for tagging.
   * @param cg The context generator used for feature creation.
   * @param tagdict The dictionary which specifies the valid set of tags for some words.
   */
  @Deprecated
  public POSTaggerME(AbstractModel model, POSContextGenerator cg, TagDictionary tagdict) {
      this(DEFAULT_BEAM_SIZE, model, cg, tagdict);
    }

  /**
   * Creates a new tagger with the specified beam size, model, context generator, and tag dictionary.
   *
   * @param beamSize The number of alternate tagging considered when tagging.
   * @param model The model used for tagging.
   * @param cg The context generator used for feature creation.
   * @param tagdict The dictionary which specifies the valid set of tags for some words.
   */
  @Deprecated
  public POSTaggerME(int beamSize, AbstractModel model, POSContextGenerator cg, TagDictionary tagdict) {
    size = beamSize;
    posModel = model;
    contextGen = cg;
    beam = new BeamSearch<String>(size, cg, model);
    tagDictionary = tagdict;
  }

  /**
   * Returns the number of different tags predicted by this model.
   *
   * @return the number of different tags predicted by this model.
   */
  public int getNumTags() {
    return posModel.getNumOutcomes();
  }

  @Deprecated
  public List<String> tag(List<String> sentence) {
    bestSequence = beam.bestSequence(sentence.toArray(new String[sentence.size()]), null);
    return bestSequence.getOutcomes();
  }

  public String[] tag(String[] sentence) {
    return this.tag(sentence, null);
  }

  public String[] tag(String[] sentence, Object[] additionaContext) {
    bestSequence = beam.bestSequence(sentence, additionaContext);
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
    Sequence[] bestSequences = beam.bestSequences(numTaggings, sentence,null);
    String[][] tags = new String[bestSequences.length][];
    for (int si=0;si<tags.length;si++) {
      List<String> t = bestSequences[si].getOutcomes();
      tags[si] = t.toArray(new String[t.size()]);
    }
    return tags;
  }

  @Deprecated
  public Sequence[] topKSequences(List<String> sentence) {
    return beam.bestSequences(size, sentence.toArray(new String[sentence.size()]), null);
  }

  public Sequence[] topKSequences(String[] sentence) {
    return this.topKSequences(sentence, null);
  }

  public Sequence[] topKSequences(String[] sentence, Object[] additionaContext) {
    return beam.bestSequences(size, sentence, additionaContext);
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
  
  public static POSModel train(String languageCode,
      ObjectStream<POSSample> samples, TrainingParameters trainParams,
      POSTaggerFactory posFactory) throws IOException {
    
    POSContextGenerator contextGenerator = posFactory.getPOSContextGenerator();
    
    Map<String, String> manifestInfoEntries = new HashMap<String, String>();
    
    AbstractModel posModel;
    
    if (!TrainUtil.isSequenceTraining(trainParams.getSettings())) {
      
      EventStream es = new POSSampleEventStream(samples, contextGenerator);
      
      posModel = TrainUtil.train(es, trainParams.getSettings(), manifestInfoEntries);
    }
    else {
      POSSampleSequenceStream ss = new POSSampleSequenceStream(samples, contextGenerator);

      posModel = TrainUtil.train(ss, trainParams.getSettings(), manifestInfoEntries);
    }
    
    return new POSModel(languageCode, posModel, manifestInfoEntries, posFactory);
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
    System.out.println("Expanding POS Dictionary ...");
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
            word = words[i].toLowerCase();
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

    System.out.println("... finished expanding POS Dictionary. ["
        + (System.nanoTime() - start) / 1000000 + "ms]");
  }
}
