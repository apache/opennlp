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

package opennlp.tools.coref.resolver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import opennlp.maxent.GIS;
import opennlp.maxent.io.SuffixSensitiveGISModelReader;
import opennlp.maxent.io.SuffixSensitiveGISModelWriter;
import opennlp.model.Event;
import opennlp.model.MaxentModel;
import opennlp.tools.coref.DiscourseEntity;
import opennlp.tools.coref.DiscourseModel;
import opennlp.tools.coref.mention.MentionContext;
import opennlp.tools.coref.sim.TestSimilarityModel;
import opennlp.tools.util.CollectionEventStream;

/**
 *  Provides common functionality used by classes which implement the {@link Resolver} class and use maximum entropy models to make resolution decisions.
 */
public abstract class MaxentResolver extends AbstractResolver {

  /** Outcomes when two mentions are coreferent. */
  public static final String SAME = "same";
  /** Outcome when two mentions are not coreferent. */
  public static final String DIFF = "diff";
  /** Default feature value. */
  public static final String DEFAULT = "default";


  private static boolean debugOn=false;

  private String modelName;
  private MaxentModel model;
  private double[] candProbs;
  private int sameIndex;
  private ResolverMode mode;
  private List<Event> events;

  /** When true, this designates that the resolver should use the first referent encountered which it
   * more preferable than non-reference.  When false all non-excluded referents within this resolvers range
   * are considered.
   */
  protected boolean preferFirstReferent;
  /** When true, this designates that training should consist of a single positive and a single negative example
   * (when possible) for each mention. */
  protected boolean pairedSampleSelection;
  
  /** When true, this designates that the same maximum entropy model should be used non-reference
   * events (the pairing of a mention and the "null" reference) as is used for potentially
   * referential pairs.  When false a separate model is created for these events.
   */
  protected boolean useSameModelForNonRef;

  private static TestSimilarityModel simModel = null;
  
  /** The model for computing non-referential probabilities. */
  protected NonReferentialResolver nonReferentialResolver;

  private static final String modelExtension = ".bin.gz";

  /**
   * Creates a maximum-entropy-based resolver which will look the specified number of entities back for a referent.
   * This constructor is only used for unit testing.
   * @param numberOfEntitiesBack
   * @param preferFirstReferent
   */
  protected MaxentResolver(int numberOfEntitiesBack, boolean preferFirstReferent) {
    super(numberOfEntitiesBack);
    this.preferFirstReferent = preferFirstReferent;
  }


  /**
   * Creates a maximum-entropy-based resolver with the specified model name, using the
   * specified mode, which will look the specified number of entities back for a referent and
   * prefer the first referent if specified.
   * @param modelDirectory The name of the directory where the resolver models are stored.
   * @param name The name of the file where this model will be read or written.
   * @param mode The mode this resolver is being using in (training, testing).
   * @param numberOfEntitiesBack The number of entities back in the text that this resolver will look
   * for a referent.
   * @param preferFirstReferent Set to true if the resolver should prefer the first referent which is more
   * likely than non-reference.  This only affects testing.
   * @param nonReferentialResolver Determines how likely it is that this entity is non-referential.
   * @throws IOException If the model file is not found or can not be written to.
   */
  public MaxentResolver(String modelDirectory, String name, ResolverMode mode, int numberOfEntitiesBack, boolean preferFirstReferent, NonReferentialResolver nonReferentialResolver) throws IOException {
    super(numberOfEntitiesBack);
    this.preferFirstReferent = preferFirstReferent;
    this.nonReferentialResolver = nonReferentialResolver;
    this.mode = mode;
    this.modelName = modelDirectory+"/"+name;
    if (ResolverMode.TEST == this.mode) {
      model = (new SuffixSensitiveGISModelReader(new File(modelName+modelExtension))).getModel();
      sameIndex = model.getIndex(SAME);
    }
    else if (ResolverMode.TRAIN == this.mode) {
      events = new ArrayList<Event>();
    }
    else {
      System.err.println("Unknown mode: " + this.mode);
    }
    //add one for non-referent possibility
    candProbs = new double[getNumEntities() + 1];
  }

  /**
   * Creates a maximum-entropy-based resolver with the specified model name, using the
   * specified mode, which will look the specified number of entities back for a referent.
   * @param modelDirectory The name of the directory where the resover models are stored.
   * @param modelName The name of the file where this model will be read or written.
   * @param mode The mode this resolver is being using in (training, testing).
   * @param numberEntitiesBack The number of entities back in the text that this resolver will look
   * for a referent.
   * @throws IOException If the model file is not found or can not be written to.
   */
  public MaxentResolver(String modelDirectory, String modelName, ResolverMode mode, int numberEntitiesBack) throws IOException {
    this(modelDirectory, modelName, mode, numberEntitiesBack, false);
  }

  public MaxentResolver(String modelDirectory, String modelName, ResolverMode mode, int numberEntitiesBack, NonReferentialResolver nonReferentialResolver) throws IOException {
    this(modelDirectory, modelName, mode, numberEntitiesBack, false,nonReferentialResolver);
  }

  public MaxentResolver(String modelDirectory, String modelName, ResolverMode mode, int numberEntitiesBack, boolean preferFirstReferent) throws IOException {
    //this(projectName, modelName, mode, numberEntitiesBack, preferFirstReferent, SingletonNonReferentialResolver.getInstance(projectName,mode));
    this(modelDirectory, modelName, mode, numberEntitiesBack, preferFirstReferent, new DefaultNonReferentialResolver(modelDirectory, modelName, mode));
  }

  public MaxentResolver(String modelDirectory, String modelName, ResolverMode mode, int numberEntitiesBack, boolean preferFirstReferent, double nonReferentialProbability) throws IOException {
    //this(projectName, modelName, mode, numberEntitiesBack, preferFirstReferent, SingletonNonReferentialResolver.getInstance(projectName,mode));
    this(modelDirectory, modelName, mode, numberEntitiesBack, preferFirstReferent, new FixedNonReferentialResolver(nonReferentialProbability));
  }

  public DiscourseEntity resolve(MentionContext ec, DiscourseModel dm) {
    DiscourseEntity de;
    int ei = 0;
    double nonReferentialProbability = nonReferentialResolver.getNonReferentialProbability(ec);
    if (debugOn) {
      System.err.println(this +".resolve: " + ec.toText() + " -> " +  "null "+nonReferentialProbability);
    }
    for (; ei < getNumEntities(dm); ei++) {
      de = dm.getEntity(ei);
      if (outOfRange(ec, de)) {
        break;
      }
      if (excluded(ec, de)) {
        candProbs[ei] = 0;
        if (debugOn) {
          System.err.println("excluded "+this +".resolve: " + ec.toText() + " -> " + de + " " + candProbs[ei]);
        }
      }
      else {

        List<String> lfeatures = getFeatures(ec, de);
        String[] features = lfeatures.toArray(new String[lfeatures.size()]);
        try {
          candProbs[ei] = model.eval(features)[sameIndex];
        }
        catch (ArrayIndexOutOfBoundsException e) {
          candProbs[ei] = 0;
        }
        if (debugOn) {
          System.err.println(this +".resolve: " + ec.toText() + " -> " + de + " ("+ec.getGender()+","+de.getGender()+") " + candProbs[ei] + " " + lfeatures);
        }
      }
      if (preferFirstReferent && candProbs[ei] > nonReferentialProbability) {
        ei++; //update for nonRef assignment
        break;
      }
    }
    candProbs[ei] = nonReferentialProbability;

    // find max
    int maxCandIndex = 0;
    for (int k = 1; k <= ei; k++) {
      if (candProbs[k] > candProbs[maxCandIndex]) {
        maxCandIndex = k;
      }
    }
    if (maxCandIndex == ei) { // no referent
      return (null);
    }
    else {
      de = dm.getEntity(maxCandIndex);
      return (de);
    }
  }


  /**
   * Returns whether the specified entity satisfies the criteria for being a default referent.
   * This criteria is used to perform sample selection on the training data and to select a single
   * non-referent entity. Typically the criteria is a heuristic for a likely referent.
   * @param de The discourse entity being considered for non-reference.
   * @return True if the entity should be used as a default referent, false otherwise.
   */
  protected boolean defaultReferent(DiscourseEntity de) {
    MentionContext ec = de.getLastExtent();
    if (ec.getNounPhraseSentenceIndex() == 0) {
      return (true);
    }
    return (false);
  }

  @Override
  public DiscourseEntity retain(MentionContext mention, DiscourseModel dm) {
    //System.err.println(this+".retain("+ec+") "+mode);
    if (ResolverMode.TRAIN == mode) {
      DiscourseEntity de = null;
      boolean referentFound = false;
      boolean hasReferentialCandidate = false;
      boolean nonReferentFound = false;
      for (int ei = 0; ei < getNumEntities(dm); ei++) {
        DiscourseEntity cde = dm.getEntity(ei);
        MentionContext entityMention = cde.getLastExtent();
        if (outOfRange(mention, cde)) {
          if (mention.getId() != -1 && !referentFound) {
            //System.err.println("retain: Referent out of range: "+ec.toText()+" "+ec.parse.getSpan());
          }
          break;
        }
        if (excluded(mention, cde)) {
          if (showExclusions) {
            if (mention.getId() != -1 && entityMention.getId() == mention.getId()) {
              System.err.println(this +".retain: Referent excluded: (" + mention.getId() + ") " + mention.toText() + " " + mention.getIndexSpan() + " -> (" + entityMention.getId() + ") " + entityMention.toText() + " " + entityMention.getSpan() + " " + this);
            }
          }
        }
        else {
          hasReferentialCandidate = true;
          boolean useAsDifferentExample = defaultReferent(cde);
          //if (!sampleSelection || (mention.getId() != -1 && entityMention.getId() == mention.getId()) || (!nonReferentFound && useAsDifferentExample)) {
            List<String> features = getFeatures(mention, cde);

            //add Event to Model
            if (debugOn) {
              System.err.println(this +".retain: " + mention.getId() + " " + mention.toText() + " -> " + entityMention.getId() + " " + cde);
            }
            if (mention.getId() != -1 && entityMention.getId() == mention.getId()) {
              referentFound = true;
              events.add(new Event(SAME, features.toArray(new String[features.size()])));
              de = cde;
              //System.err.println("MaxentResolver.retain: resolved at "+ei);
              distances.add(ei);
            }
            else if (!pairedSampleSelection || (!nonReferentFound && useAsDifferentExample)) {
              nonReferentFound = true;
              events.add(new Event(DIFF, features.toArray(new String[features.size()])));
            }
          //}
        }
        if (pairedSampleSelection && referentFound && nonReferentFound) {
          break;
        }
        if (preferFirstReferent && referentFound) {
          break;
        }
      }
      // doesn't refer to anything
      if (hasReferentialCandidate) {
        nonReferentialResolver.addEvent(mention);
      }
      return (de);
    }
    else {
      return (super.retain(mention, dm));
    }
  }

  /**
   * Returns a list of features for deciding whether the specified mention refers to the specified discourse entity.
   * @param mention the mention being considers as possibly referential.
   * @param entity The discourse entity with which the mention is being considered referential.
   * @return a list of features used to predict reference between the specified mention and entity.
   */
  protected List<String> getFeatures(MentionContext mention, DiscourseEntity entity) {
    List<String> features = new ArrayList<String>();
    features.add(DEFAULT);
    features.addAll(ResolverUtils.getCompatibilityFeatures(mention, entity,simModel));
    return features;
  }

  @Override
  public void train() throws IOException {
    if (ResolverMode.TRAIN == mode) {
      if (debugOn) {
        System.err.println(this +" referential");
        FileWriter writer = new FileWriter(modelName+".events");
        for (Iterator<Event> ei=events.iterator();ei.hasNext();) {
          Event e = ei.next();
          writer.write(e.toString()+"\n");
        }
        writer.close();
      }
      (new SuffixSensitiveGISModelWriter(GIS.trainModel(new CollectionEventStream(events),100,10),new File(modelName+modelExtension))).persist();
      nonReferentialResolver.train();
    }
  }

  public static void setSimilarityModel(TestSimilarityModel sm) {
    simModel = sm;
  }

  @Override
  protected boolean excluded(MentionContext ec, DiscourseEntity de) {
    if (super.excluded(ec, de)) {
      return true;
    }
    return false;
    /*
    else {
      if (GEN_INCOMPATIBLE == getGenderCompatibilityFeature(ec,de)) {
        return true;
      }
      else if (NUM_INCOMPATIBLE == getNumberCompatibilityFeature(ec,de)) {
        return true;
      }
      else if (SIM_INCOMPATIBLE == getSemanticCompatibilityFeature(ec,de)) {
        return true;
      }
      return false;
    }
    */
  }
}
