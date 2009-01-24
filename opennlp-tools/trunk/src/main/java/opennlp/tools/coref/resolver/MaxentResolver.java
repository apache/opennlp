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

package opennlp.tools.coref.resolver;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import opennlp.maxent.GIS;
import opennlp.maxent.io.BinaryGISModelReader;
import opennlp.maxent.io.SuffixSensitiveGISModelReader;
import opennlp.maxent.io.SuffixSensitiveGISModelWriter;
import opennlp.model.Event;
import opennlp.model.MaxentModel;
import opennlp.tools.coref.DiscourseEntity;
import opennlp.tools.coref.DiscourseModel;
import opennlp.tools.coref.Linker;
import opennlp.tools.coref.mention.MentionContext;
import opennlp.tools.coref.mention.Parse;
import opennlp.tools.coref.sim.GenderEnum;
import opennlp.tools.coref.sim.NumberEnum;
import opennlp.tools.coref.sim.TestSimilarityModel;
import opennlp.tools.util.CollectionEventStream;

/**
 *  Provides common functionality used by classes which implement the {@link Resolver} class and use maximum entropy models to make resolution decisions.
 */
public abstract class MaxentResolver extends AbstractResolver {

  /** Outcomes when two mentions are coreferent. */
  public static final String SAME = "same";
  /** Outcome when two mentions are not corefernt. */
  public static final String DIFF = "diff";
  /** Default feature value. */
  public static final String DEFAULT = "default";

  private static final Pattern ENDS_WITH_PERIOD = Pattern.compile("\\.$");
  private static final double MIN_SIM_PROB = 0.60;

  private static final String SIM_COMPATIBLE = "sim.compatible";
  private static final String SIM_INCOMPATIBLE = "sim.incompatible";
  private static final String SIM_UNKNOWN = "sim.unknown";

  private static final String NUM_COMPATIBLE = "num.compatible";
  private static final String NUM_INCOMPATIBLE = "num.incompatible";
  private static final String NUM_UNKNOWN = "num.unknown";

  private static final String GEN_COMPATIBLE = "gen.compatible";
  private static final String GEN_INCOMPATIBLE = "gen.incompatible";
  private static final String GEN_UNKNOWN = "gen.unknown";

  private static boolean debugOn=false;

  private static boolean loadAsResource=false;

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
  /** When true, this designates that training should consist of a single positive and a single negitive example
   * (when possible) for each mention. */
  protected boolean pairedSampleSelection;
  /** When true, this designates that the same maximum entropy model should be used non-reference
   * events (the pairing of a mention and the "null" reference) as is used for potentially
   * referential pairs.  When false a seperate model is created for these events.
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
   * @param modelDirectory The name of the directory where the resover models are stored.
   * @param name The name of the file where this model will be read or written.
   * @param mode The mode this resolver is being using in (training, testing).
   * @param numberOfEntitiesBack The number of entities back in the text that this resolver will look
   * for a referent.
   * @param preferFirstReferent Set to true if the resolver should prefer the first referent which is more
   * likly than non-reference.  This only affects testing.
   * @param nonReferentialResolver Determines how likly it is that this entity is non-referential.
   * @throws IOException If the model file is not found or can not be written to.
   */
  public MaxentResolver(String modelDirectory, String name, ResolverMode mode, int numberOfEntitiesBack, boolean preferFirstReferent, NonReferentialResolver nonReferentialResolver) throws IOException {
    super(numberOfEntitiesBack);
    this.preferFirstReferent = preferFirstReferent;
    this.nonReferentialResolver = nonReferentialResolver;
    this.mode = mode;
    this.modelName = modelDirectory+"/"+name;
    if (ResolverMode.TEST == this.mode) {
      if (loadAsResource) {
        model = (new BinaryGISModelReader(new DataInputStream(this.getClass().getResourceAsStream(modelName+modelExtension)))).getModel();
      }
      else {
        model = (new SuffixSensitiveGISModelReader(new File(modelName+modelExtension))).getModel();
      }
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

  /**
   * Specifies whether the models should be loaded from a resource.
   * @param lar boolean which if true indicates that the model should be loaded as a resource.
   */
  public static void loadAsResource(boolean lar) {
    loadAsResource = lar;
  }

  /**
   * Returns whether the models should be loaded from a file or from a resource.
   * @return  whether the models should be loaded from a file or from a resource.
   */
  public static boolean loadAsResource() {
    return loadAsResource;
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

  /*
  protected double getNonReferentialProbability(MentionContext ec) {
    if (useFixedNonReferentialProbability) {
      if (debugOn) {
        System.err.println(this +".resolve: " + ec.toText() + " -> " + null +" " + fixedNonReferentialProbability);
        System.err.println();
      }
      return fixedNonReferentialProbability;
    }
    List lfeatures = getFeatures(ec, null);
    String[] features = (String[]) lfeatures.toArray(new String[lfeatures.size()]);

    if (features == null) {
      System.err.println("features=null in " + this);
    }
    if (model == null) {
      System.err.println("model=null in " + this);
    }
    double[] dist = nrModel.eval(features);

    if (dist == null) {
      System.err.println("dist=null in " + this);
    }
    if (debugOn) {
      System.err.println(this +".resolve: " + ec.toText() + " -> " + null +" " + dist[nrSameIndex] + " " + lfeatures);
      System.err.println();
    }
    return (dist[nrSameIndex]);
  }
  */

  /**
   * Returns whether the specified entity satisfies the criteria for being a default referent.
   * This criteria is used to perform sample selection on the training data and to select a single
   * non-referent entity. Typcically the criteria is a hueristic for a likly referent.
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
              distances.add(new Integer(ei));
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

  protected String getMentionCountFeature(DiscourseEntity de) {
    if (de.getNumMentions() >= 5) {
      return ("mc=5+");
    }
    else {
      return ("mc=" + de.getNumMentions());
    }
  }



  /**
   * Returns a list of features for deciding whether the specified mention refers to the specified discourse entity.
   * @param mention the mention being considers as possibly referential.
   * @param entity The disource entity with which the mention is being considered referential.
   * @return a list of features used to predict reference between the specified mention and entity.
   */
  protected List<String> getFeatures(MentionContext mention, DiscourseEntity entity) {
    List<String> features = new ArrayList<String>();
    features.add(DEFAULT);
    features.addAll(getCompatibilityFeatures(mention, entity));
    return features;
  }

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

  private String getSemanticCompatibilityFeature(MentionContext ec, DiscourseEntity de) {
    if (simModel != null) {
      double best = 0;
      for (Iterator<MentionContext> xi = de.getMentions(); xi.hasNext();) {
        MentionContext ec2 = xi.next();
        double sim = simModel.compatible(ec, ec2);
        if (debugOn) {
          System.err.println("MaxentResolver.getSemanticCompatibilityFeature: sem-compat " + sim + " " + ec.toText() + " " + ec2.toText());
        }
        if (sim > best) {
          best = sim;
        }
      }
      if (best > MIN_SIM_PROB) {
        return SIM_COMPATIBLE;
      }
      else if (best > (1 - MIN_SIM_PROB)) {
        return SIM_UNKNOWN;
      }
      else {
        return SIM_INCOMPATIBLE;
      }
    }
    else {
      System.err.println("MaxentResolver: Uninitialized Semantic Model");
      return SIM_UNKNOWN;
    }
  }

  private String getGenderCompatibilityFeature(MentionContext ec, DiscourseEntity de) {
    GenderEnum eg = de.getGender();
    //System.err.println("getGenderCompatibility: mention="+ec.getGender()+" entity="+eg);
    if (eg == GenderEnum.UNKNOWN || ec.getGender() == GenderEnum.UNKNOWN) {
      return GEN_UNKNOWN;
    }
    else if (ec.getGender() == eg) {
      return GEN_COMPATIBLE;
    }
    else {
      return GEN_INCOMPATIBLE;
    }
  }

  private String getNumberCompatibilityFeature(MentionContext ec, DiscourseEntity de) {
    NumberEnum en = de.getNumber();
    if (en == NumberEnum.UNKNOWN || ec.getNumber() == NumberEnum.UNKNOWN) {
      return NUM_UNKNOWN;
    }
    else if (ec.getNumber() == en) {
      return NUM_COMPATIBLE;
    }
    else {
      return NUM_INCOMPATIBLE;
    }
  }

  /**
   * Returns features indicating whether the specified mention and the specified entity are compatible.
   * @param mention The mention.
   * @param entity The entity.
   * @return list of features indicating whether the specified mention and the specified entity are compatible.
   */
  private List<String> getCompatibilityFeatures(MentionContext mention, DiscourseEntity entity) {
    List<String> compatFeatures = new ArrayList<String>();
    String semCompatible = getSemanticCompatibilityFeature(mention, entity);
    compatFeatures.add(semCompatible);
    String genCompatible = getGenderCompatibilityFeature(mention, entity);
    compatFeatures.add(genCompatible);
    String numCompatible = getNumberCompatibilityFeature(mention, entity);
    compatFeatures.add(numCompatible);
    if (semCompatible.equals(SIM_COMPATIBLE) && genCompatible.equals(GEN_COMPATIBLE) && numCompatible.equals(NUM_COMPATIBLE)) {
      compatFeatures.add("all.compatible");
    }
    else if (semCompatible.equals(SIM_INCOMPATIBLE) || genCompatible.equals(GEN_INCOMPATIBLE) || numCompatible.equals(NUM_INCOMPATIBLE)) {
      compatFeatures.add("some.incompatible");
    }
    return compatFeatures;
  }

  /**
   * Returns a list of features based on the surrounding context of the specified mention.
   * @param mention he mention whose surround context the features model.
   * @return a list of features based on the surrounding context of the specified mention
   */
  public static List<String> getContextFeatures(MentionContext mention) {
    List<String> features = new ArrayList<String>();
    if (mention.getPreviousToken() != null) {
      features.add("pt=" + mention.getPreviousToken().getSyntacticType());
      features.add("pw=" + mention.getPreviousToken().toString());
    }
    else {
      features.add("pt=BOS");
      features.add("pw=BOS");
    }
    if (mention.getNextToken() != null) {
      features.add("nt=" + mention.getNextToken().getSyntacticType());
      features.add("nw=" + mention.getNextToken().toString());
    }
    else {
      features.add("nt=EOS");
      features.add("nw=EOS");
    }
    if (mention.getNextTokenBasal() != null) {
      features.add("bnt=" + mention.getNextTokenBasal().getSyntacticType());
      features.add("bnw=" + mention.getNextTokenBasal().toString());
    }
    else {
      features.add("bnt=EOS");
      features.add("bnw=EOS");
    }
    return (features);
  }

  private Set<String> constructModifierSet(Parse[] tokens, int headIndex) {
    Set<String> modSet = new HashSet<String>();
    for (int ti = 0; ti < headIndex; ti++) {
      Parse tok = tokens[ti];
      modSet.add(tok.toString().toLowerCase());
    }
    return (modSet);
  }

  /**
   * Returns whether the specified token is a definite article.
   * @param tok The token.
   * @param tag The pos-tag for the specified token.
   * @return whether the specified token is a definite article.
   */
  protected boolean definiteArticle(String tok, String tag) {
    tok = tok.toLowerCase();
    if (tok.equals("the") || tok.equals("these") || tok.equals("these") || tag.equals("PRP$")) {
      return (true);
    }
    return (false);
  }

  private boolean isSubstring(String ecStrip, String xecStrip) {
    //System.err.println("MaxentResolver.isSubstring: ec="+ecStrip+" xec="+xecStrip);
    int io = xecStrip.indexOf(ecStrip);
    if (io != -1) {
      //check boundries
      if (io != 0 && xecStrip.charAt(io - 1) != ' ') {
        return false;
      }
      int end = io + ecStrip.length();
      if (end != xecStrip.length() && xecStrip.charAt(end) != ' ') {
        return false;
      }
      return true;
    }
    return false;
  }

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

  /**
   * Returns distance features for the specified mention and entity.
   * @param mention The mention.
   * @param entity The entity.
   * @return list of distance features for the specified mention and entity.
   */
  protected List<String> getDistanceFeatures(MentionContext mention, DiscourseEntity entity) {
    List<String> features = new ArrayList<String>();
    MentionContext cec = entity.getLastExtent();
    int entityDistance = mention.getNounPhraseDocumentIndex()- cec.getNounPhraseDocumentIndex();
    int sentenceDistance = mention.getSentenceNumber() - cec.getSentenceNumber();
    int hobbsEntityDistance;
    if (sentenceDistance == 0) {
      hobbsEntityDistance = cec.getNounPhraseSentenceIndex();
    }
    else {
      //hobbsEntityDistance = entityDistance - (entities within sentence from mention to end) + (entities within sentence form start to mention)
      //hobbsEntityDistance = entityDistance - (cec.maxNounLocation - cec.getNounPhraseSentenceIndex) + cec.getNounPhraseSentenceIndex;
      hobbsEntityDistance = entityDistance + (2 * cec.getNounPhraseSentenceIndex()) - cec.getMaxNounPhraseSentenceIndex();
    }
    features.add("hd=" + hobbsEntityDistance);
    features.add("de=" + entityDistance);
    features.add("ds=" + sentenceDistance);
    //features.add("ds=" + sdist + pronoun);
    //features.add("dn=" + cec.sentenceNumber);
    //features.add("ep=" + cec.nounLocation);
    return (features);
  }

  private Map<String, String> getPronounFeatureMap(String pronoun) {
    Map<String, String> pronounMap = new HashMap<String, String>();
    if (Linker.malePronounPattern.matcher(pronoun).matches()) {
      pronounMap.put("gender","male");
    }
    else if (Linker.femalePronounPattern.matcher(pronoun).matches()) {
      pronounMap.put("gender","female");
    }
    else if (Linker.neuterPronounPattern.matcher(pronoun).matches()) {
      pronounMap.put("gender","neuter");
    }
    if (Linker.singularPronounPattern.matcher(pronoun).matches()) {
      pronounMap.put("number","singular");
    }
    else if (Linker.pluralPronounPattern.matcher(pronoun).matches()) {
      pronounMap.put("number","plural");
    }
    /*
    if (Linker.firstPersonPronounPattern.matcher(pronoun).matches()) {
      pronounMap.put("person","first");
    }
    else if (Linker.secondPersonPronounPattern.matcher(pronoun).matches()) {
      pronounMap.put("person","second");
    }
    else if (Linker.thirdPersonPronounPattern.matcher(pronoun).matches()) {
      pronounMap.put("person","third");
    }
    */
    return pronounMap;
  }

  /**
   * Returns features indicating whether the specified mention is compatible with the pronouns
   * of the specified entity.
   * @param mention The mention.
   * @param entity The entity.
   * @return list of features indicating whether the specified mention is compatible with the pronouns
   * of the specified entity.
   */
  protected List<String> getPronounMatchFeatures(MentionContext mention, DiscourseEntity entity) {
    boolean foundCompatiblePronoun = false;
    boolean foundIncompatiblePronoun = false;
    if (mention.getHeadTokenTag().startsWith("PRP")) {
      Map<String, String> pronounMap = getPronounFeatureMap(mention.getHeadTokenText());
      //System.err.println("getPronounMatchFeatures.pronounMap:"+pronounMap);
      for (Iterator<MentionContext> mi=entity.getMentions();mi.hasNext();) {
        MentionContext candidateMention = mi.next();
        if (candidateMention.getHeadTokenTag().startsWith("PRP")) {
          if (mention.getHeadTokenText().equalsIgnoreCase(candidateMention.getHeadTokenText())) {
            foundCompatiblePronoun = true;
            break;
          }
          else {
            Map<String, String> candidatePronounMap = getPronounFeatureMap(candidateMention.getHeadTokenText());
            //System.err.println("getPronounMatchFeatures.candidatePronounMap:"+candidatePronounMap);
            boolean allKeysMatch = true;
            for (Iterator<String> ki = pronounMap.keySet().iterator(); ki.hasNext();) {
              String key = ki.next();
              String cfv = candidatePronounMap.get(key);
              if (cfv != null) {
                if (!pronounMap.get(key).equals(cfv)) {
                  foundIncompatiblePronoun = true;
                  allKeysMatch = false;
                }
              }
              else {
                allKeysMatch = false;
              }
            }
            if (allKeysMatch) {
              foundCompatiblePronoun = true;
            }
          }
        }
      }
    }
    List<String> pronounFeatures = new ArrayList<String>();
    if (foundCompatiblePronoun) {
      pronounFeatures.add("compatiblePronoun");
    }
    if (foundIncompatiblePronoun) {
      pronounFeatures.add("incompatiblePronoun");
    }
    return pronounFeatures;
  }

  /**
   * Returns string-match features for the the specified mention and entity.
   * @param mention The mention.
   * @param entity The entity.
   * @return list of string-match features for the the specified mention and entity.
   */
  protected List<String> getStringMatchFeatures(MentionContext mention, DiscourseEntity entity) {
    boolean sameHead = false;
    boolean modsMatch = false;
    boolean titleMatch = false;
    boolean nonTheModsMatch = false;
    List<String> features = new ArrayList<String>();
    Parse[] mtokens = mention.getTokenParses();
    Set<String> ecModSet = constructModifierSet(mtokens, mention.getHeadTokenIndex());
    String mentionHeadString = mention.getHeadTokenText().toLowerCase();
    Set<String> featureSet = new HashSet<String>();
    for (Iterator<MentionContext> ei = entity.getMentions(); ei.hasNext();) {
      MentionContext entityMention = ei.next();
      String exactMatchFeature = getExactMatchFeature(entityMention, mention);
      if (exactMatchFeature != null) {
        featureSet.add(exactMatchFeature);
      }
      else if (entityMention.getParse().isCoordinatedNounPhrase() && !mention.getParse().isCoordinatedNounPhrase()) {
        featureSet.add("cmix");
      }
      else {
        String mentionStrip = stripNp(mention);
        String entityMentionStrip = stripNp(entityMention);
        if (mentionStrip != null && entityMentionStrip != null) {
          if (isSubstring(mentionStrip, entityMentionStrip)) {
            featureSet.add("substring");
          }
        }
      }
      Parse[] xtoks = entityMention.getTokenParses();
      int headIndex = entityMention.getHeadTokenIndex();
      //if (!mention.getHeadTokenTag().equals(entityMention.getHeadTokenTag())) {
      //  //System.err.println("skipping "+mention.headTokenText+" with "+xec.headTokenText+" because "+mention.headTokenTag+" != "+xec.headTokenTag);
      //  continue;
      //}  want to match NN NNP
      String entityMentionHeadString = entityMention.getHeadTokenText().toLowerCase();
      // model lexical similarity
      if (mentionHeadString.equals(entityMentionHeadString)) {
        sameHead = true;
        featureSet.add("hds=" + mentionHeadString);
        if (!modsMatch || !nonTheModsMatch) { //only check if we haven't already found one which is the same
          modsMatch = true;
          nonTheModsMatch = true;
          Set<String> entityMentionModifierSet = constructModifierSet(xtoks, headIndex);
          for (Iterator<String> mi = ecModSet.iterator(); mi.hasNext();) {
            String mw = mi.next();
            if (!entityMentionModifierSet.contains(mw)) {
              modsMatch = false;
              if (!mw.equals("the")) {
                nonTheModsMatch = false;
                featureSet.add("mmw=" + mw);
              }
            }
          }
        }
      }
      Set<String> descModSet = constructModifierSet(xtoks, entityMention.getNonDescriptorStart());
      if (descModSet.contains(mentionHeadString)) {
        titleMatch = true;
      }
    }
    if (!featureSet.isEmpty()) {
      features.addAll(featureSet);
    }
    if (sameHead) {
      features.add("sameHead");
      if (modsMatch) {
        features.add("modsMatch");
      }
      else if (nonTheModsMatch) {
        features.add("nonTheModsMatch");
      }
      else {
        features.add("modsMisMatch");
      }
    }
    if (titleMatch) {
      features.add("titleMatch");
    }
    return features;
  }


  private String mentionString(MentionContext ec) {
    StringBuffer sb = new StringBuffer();
    Object[] mtokens = ec.getTokens();
    sb.append(mtokens[0].toString());
    for (int ti = 1, tl = mtokens.length; ti < tl; ti++) {
      String token = mtokens[ti].toString();
      sb.append(" ").append(token);
    }
    //System.err.println("mentionString "+ec+" == "+sb.toString()+" mtokens.length="+mtokens.length);
    return sb.toString();
  }

  private String excludedTheMentionString(MentionContext ec) {
    StringBuffer sb = new StringBuffer();
    boolean first = true;
    Object[] mtokens = ec.getTokens();
    for (int ti = 0, tl = mtokens.length; ti < tl; ti++) {
      String token = mtokens[ti].toString();
      if (!token.equals("the") && !token.equals("The") && !token.equals("THE")) {
        if (!first) {
          sb.append(" ");
        }
        sb.append(token);
        first = false;
      }
    }
    return sb.toString();
  }

  private String excludedHonorificMentionString(MentionContext ec) {
    StringBuffer sb = new StringBuffer();
    boolean first = true;
    Object[] mtokens = ec.getTokens();
    for (int ti = 0, tl = mtokens.length; ti < tl; ti++) {
      String token = mtokens[ti].toString();
      if (!Linker.honorificsPattern.matcher(token).matches()) {
        if (!first) {
          sb.append(" ");
        }
        sb.append(token);
        first = false;
      }
    }
    return sb.toString();
  }

  private String excludedDeterminerMentionString(MentionContext ec) {
    StringBuffer sb = new StringBuffer();
    boolean first = true;
    Parse[] mtokens = ec.getTokenParses();
    for (int ti = 0, tl = mtokens.length; ti < tl; ti++) {
      Parse token = mtokens[ti];
      String tag = token.getSyntacticType();
      if (!tag.equals("DT")) {
        if (!first) {
          sb.append(" ");
        }
        sb.append(token.toString());
        first = false;
      }
    }
    return sb.toString();
  }

  private String getExactMatchFeature(MentionContext ec, MentionContext xec) {
    //System.err.println("getExactMatchFeature: ec="+mentionString(ec)+" mc="+mentionString(xec));
    if (mentionString(ec).equals(mentionString(xec))) {
      return "exactMatch";
    }
    else if (excludedHonorificMentionString(ec).equals(excludedHonorificMentionString(xec))) {
      return "exactMatchNoHonor";
    }
    else if (excludedTheMentionString(ec).equals(excludedTheMentionString(xec))) {
      return "exactMatchNoThe";
    }
    else if (excludedDeterminerMentionString(ec).equals(excludedDeterminerMentionString(xec))) {
      return "exactMatchNoDT";
    }
    return null;
  }

  /**
   * Returns a list of word features for the specified tokens.
   * @param token The token for which fetures are to be computed.
   * @return a list of word features for the specified tokens.
   */
  public static List<String> getWordFeatures(Parse token) {
    List<String> wordFeatures = new ArrayList<String>();
    String word = token.toString().toLowerCase();
    String wf = "";
    if (ENDS_WITH_PERIOD.matcher(word).find()) {
      wf = ",endWithPeriod";
    }
    String tokTag = token.getSyntacticType();
    wordFeatures.add("w=" + word + ",t=" + tokTag + wf);
    wordFeatures.add("t=" + tokTag + wf);
    return wordFeatures;
  }
}
