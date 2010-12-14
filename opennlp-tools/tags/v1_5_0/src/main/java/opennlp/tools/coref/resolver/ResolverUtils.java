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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import opennlp.tools.coref.DiscourseEntity;
import opennlp.tools.coref.mention.MentionContext;
import opennlp.tools.coref.mention.Parse;
import opennlp.tools.coref.sim.GenderEnum;
import opennlp.tools.coref.sim.NumberEnum;
import opennlp.tools.coref.sim.TestSimilarityModel;

/**
 * This class provides a set of utilities for turning mentions into normalized strings and features.
 */
public class ResolverUtils {
  
  private static final Pattern ENDS_WITH_PERIOD = Pattern.compile("\\.$");
  private static final Pattern initialCaps = Pattern.compile("^[A-Z]");

  /** Regular expression for English singular third person pronouns. */
  public static final Pattern singularThirdPersonPronounPattern = Pattern.compile("^(he|she|it|him|her|his|hers|its|himself|herself|itself)$",Pattern.CASE_INSENSITIVE);
  /** Regular expression for English plural third person pronouns. */
  public static final Pattern pluralThirdPersonPronounPattern = Pattern.compile("^(they|their|theirs|them|themselves)$",Pattern.CASE_INSENSITIVE);
  /** Regular expression for English speech pronouns. */
  public static final Pattern speechPronounPattern = Pattern.compile("^(I|me|my|you|your|you|we|us|our|ours)$",Pattern.CASE_INSENSITIVE);
  /** Regular expression for English female pronouns. */
  public static final Pattern femalePronounPattern = Pattern.compile("^(she|her|hers|herself)$",Pattern.CASE_INSENSITIVE);
  /** Regular expression for English neuter pronouns. */
  public static final Pattern neuterPronounPattern = Pattern.compile("^(it|its|itself)$",Pattern.CASE_INSENSITIVE);
  /** Regular expression for English first person pronouns. */
  public static final Pattern firstPersonPronounPattern = Pattern.compile("^(I|me|my|we|our|us|ours)$",Pattern.CASE_INSENSITIVE);
  /** Regular expression for English singular second person pronouns. */
  public static final Pattern secondPersonPronounPattern = Pattern.compile("^(you|your|yours)$",Pattern.CASE_INSENSITIVE);
  /** Regular expression for English third person pronouns. */
  public static final Pattern thirdPersonPronounPattern = Pattern.compile("^(he|she|it|him|her|his|hers|its|himself|herself|itself|they|their|theirs|them|themselves)$",Pattern.CASE_INSENSITIVE);
  /** Regular expression for English singular pronouns. */
  public static final Pattern singularPronounPattern = Pattern.compile("^(I|me|my|he|she|it|him|her|his|hers|its|himself|herself|itself)$",Pattern.CASE_INSENSITIVE);
  /** Regular expression for English plural pronouns. */
  public static final Pattern pluralPronounPattern = Pattern.compile("^(we|us|our|ours|they|their|theirs|them|themselves)$",Pattern.CASE_INSENSITIVE);
  /** Regular expression for English male pronouns. */
  public static final Pattern malePronounPattern = Pattern.compile("^(he|him|his|himself)$",Pattern.CASE_INSENSITIVE);
  /** Regular expression for English honorifics. */
  public static final Pattern honorificsPattern = Pattern.compile("[A-Z][a-z]+\\.$|^[A-Z][b-df-hj-np-tv-xz]+$");
  /** Regular expression for English corporate designators. */
  public static final Pattern designatorsPattern = Pattern.compile("[a-z]\\.$|^[A-Z][b-df-hj-np-tv-xz]+$|^Co(rp)?$");

  
  private static final String NUM_COMPATIBLE = "num.compatible";
  private static final String NUM_INCOMPATIBLE = "num.incompatible";
  private static final String NUM_UNKNOWN = "num.unknown";
  
  private static final String GEN_COMPATIBLE = "gen.compatible";
  private static final String GEN_INCOMPATIBLE = "gen.incompatible";
  private static final String GEN_UNKNOWN = "gen.unknown";
  private static final String SIM_COMPATIBLE = "sim.compatible";
  private static final String SIM_INCOMPATIBLE = "sim.incompatible";
  private static final String SIM_UNKNOWN = "sim.unknown";

  
  private static final double MIN_SIM_PROB = 0.60;



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

  /**
   * Returns a list of word features for the specified tokens.
   * @param token The token for which features are to be computed.
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

  public static Set<String> constructModifierSet(Parse[] tokens, int headIndex) {
    Set<String> modSet = new HashSet<String>();
    for (int ti = 0; ti < headIndex; ti++) {
      Parse tok = tokens[ti];
      modSet.add(tok.toString().toLowerCase());
    }
    return (modSet);
  }

  public static String excludedDeterminerMentionString(MentionContext ec) {
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

  public static String excludedHonorificMentionString(MentionContext ec) {
    StringBuffer sb = new StringBuffer();
    boolean first = true;
    Object[] mtokens = ec.getTokens();
    for (int ti = 0, tl = mtokens.length; ti < tl; ti++) {
      String token = mtokens[ti].toString();
      if (!honorificsPattern.matcher(token).matches()) {
        if (!first) {
          sb.append(" ");
        }
        sb.append(token);
        first = false;
      }
    }
    return sb.toString();
  }

  public static String excludedTheMentionString(MentionContext ec) {
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

  public static String getExactMatchFeature(MentionContext ec, MentionContext xec) {
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
   * Returns string-match features for the the specified mention and entity.
   * @param mention The mention.
   * @param entity The entity.
   * @return list of string-match features for the the specified mention and entity.
   */
  public static List<String> getStringMatchFeatures(MentionContext mention, DiscourseEntity entity) {
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

  public static boolean isSubstring(String ecStrip, String xecStrip) {
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

  public static String mentionString(MentionContext ec) {
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

  /**
   * Returns a string for the specified mention with punctuation, honorifics,
   * designators, and determiners removed.
   * 
   * @param mention The mention to be striped.
   * 
   * @return a normalized string representation of the specified mention.
   */
  public static String stripNp(MentionContext mention) {
    int start=mention.getNonDescriptorStart(); //start after descriptors
  
    Parse[] mtokens = mention.getTokenParses();
    int end=mention.getHeadTokenIndex()+1;
    if (start == end) {
      //System.err.println("stripNp: return null 1");
      return null;
    }
    //strip determiners
    if (mtokens[start].getSyntacticType().equals("DT")) {
      start++;
    }
    if (start == end) {
      //System.err.println("stripNp: return null 2");
      return null;
    }
    //get to first NNP
    String type;
    for (int i=start;i<end;i++) {
      type = mtokens[start].getSyntacticType();
      if (type.startsWith("NNP")) {
        break;
      }
      start++;
    }
    if (start == end) {
      //System.err.println("stripNp: return null 3");
      return null;
    }
    if (start+1 != end) { // don't do this on head words, to keep "U.S."
      //strip off honorifics in begining
      if (honorificsPattern.matcher(mtokens[start].toString()).find()) {
        start++;
      }
      if (start == end) {
        //System.err.println("stripNp: return null 4");
        return null;
      }
      //strip off and honerifics on the end
      if (designatorsPattern.matcher(mtokens[mtokens.length - 1].toString()).find()) {
        end--;
      }
    }
    if (start == end) {
      //System.err.println("stripNp: return null 5");
      return null;
    }
    String strip = "";
    for (int i = start; i < end; i++) {
      strip += mtokens[i].toString() + ' ';
    }
    return strip.trim();
  }

  public static MentionContext getProperNounExtent(DiscourseEntity de) {
    for (Iterator<MentionContext> ei = de.getMentions(); ei.hasNext();) { //use first extent which is propername
      MentionContext xec = ei.next();
      String xecHeadTag = xec.getHeadTokenTag();
      if (xecHeadTag.startsWith("NNP") || initialCaps.matcher(xec.getHeadTokenText()).find()) {
        return xec;
      }
    }
    return null;
  }

  private static Map<String, String> getPronounFeatureMap(String pronoun) {
    Map<String, String> pronounMap = new HashMap<String, String>();
    if (malePronounPattern.matcher(pronoun).matches()) {
      pronounMap.put("gender","male");
    }
    else if (femalePronounPattern.matcher(pronoun).matches()) {
      pronounMap.put("gender","female");
    }
    else if (neuterPronounPattern.matcher(pronoun).matches()) {
      pronounMap.put("gender","neuter");
    }
    if (singularPronounPattern.matcher(pronoun).matches()) {
      pronounMap.put("number","singular");
    }
    else if (pluralPronounPattern.matcher(pronoun).matches()) {
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
  public static List<String> getPronounMatchFeatures(MentionContext mention, DiscourseEntity entity) {
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
   * Returns distance features for the specified mention and entity.
   * @param mention The mention.
   * @param entity The entity.
   * @return list of distance features for the specified mention and entity.
   */
  public static List<String> getDistanceFeatures(MentionContext mention, DiscourseEntity entity) {
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

  /**
   * Returns whether the specified token is a definite article.
   * @param tok The token.
   * @param tag The pos-tag for the specified token.
   * @return whether the specified token is a definite article.
   */
  public static boolean definiteArticle(String tok, String tag) {
    tok = tok.toLowerCase();
    if (tok.equals("the") || tok.equals("these") || tok.equals("these") || tag.equals("PRP$")) {
      return (true);
    }
    return (false);
  }

  public static String getNumberCompatibilityFeature(MentionContext ec, DiscourseEntity de) {
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
  public static List<String> getCompatibilityFeatures(MentionContext mention, DiscourseEntity entity, TestSimilarityModel simModel) {
    List<String> compatFeatures = new ArrayList<String>();
    String semCompatible = getSemanticCompatibilityFeature(mention, entity, simModel);
    compatFeatures.add(semCompatible);
    String genCompatible = getGenderCompatibilityFeature(mention, entity);
    compatFeatures.add(genCompatible);
    String numCompatible = ResolverUtils.getNumberCompatibilityFeature(mention, entity);
    compatFeatures.add(numCompatible);
    if (semCompatible.equals(SIM_COMPATIBLE) && genCompatible.equals(GEN_COMPATIBLE) && numCompatible.equals(ResolverUtils.NUM_COMPATIBLE)) {
      compatFeatures.add("all.compatible");
    }
    else if (semCompatible.equals(SIM_INCOMPATIBLE) || genCompatible.equals(GEN_INCOMPATIBLE) || numCompatible.equals(ResolverUtils.NUM_INCOMPATIBLE)) {
      compatFeatures.add("some.incompatible");
    }
    return compatFeatures;
  }

  public static String getGenderCompatibilityFeature(MentionContext ec, DiscourseEntity de) {
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

  public static String getSemanticCompatibilityFeature(MentionContext ec, DiscourseEntity de, TestSimilarityModel simModel) {
    if (simModel != null) {
      double best = 0;
      for (Iterator<MentionContext> xi = de.getMentions(); xi.hasNext();) {
        MentionContext ec2 = xi.next();
        double sim = simModel.compatible(ec, ec2);
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

  public static String getMentionCountFeature(DiscourseEntity de) {
    if (de.getNumMentions() >= 5) {
      return ("mc=5+");
    }
    else {
      return ("mc=" + de.getNumMentions());
    }
  }

  /**
   * Returns a string representing the gender of the specified pronoun.
   * @param pronoun An English pronoun.
   * @return the gender of the specified pronoun.
   */
  public static String getPronounGender(String pronoun) {
    if (malePronounPattern.matcher(pronoun).matches()) {
      return "m";
    }
    else if (femalePronounPattern.matcher(pronoun).matches()) {
      return "f";
    }
    else if (neuterPronounPattern.matcher(pronoun).matches()) {
      return "n";
    }
    else {
      return "u";
    }
  }
  
  

}
