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


package opennlp.tools.coref.sim;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import opennlp.maxent.GIS;
import opennlp.maxent.io.SuffixSensitiveGISModelReader;
import opennlp.maxent.io.SuffixSensitiveGISModelWriter;
import opennlp.model.Event;
import opennlp.model.MaxentModel;
import opennlp.tools.coref.resolver.ResolverUtils;
import opennlp.tools.util.CollectionEventStream;
import opennlp.tools.util.HashList;

/**
 * Class which models the gender of a particular mentions and entities made up of mentions.
 */
public class GenderModel implements TestGenderModel, TrainSimilarityModel {

  private int maleIndex;
  private int femaleIndex;
  private int neuterIndex;

  private String modelName;
  private String modelExtension = ".bin.gz";
  private MaxentModel testModel;
  private List<Event> events;
  private boolean debugOn = true;

  private Set<String> maleNames;
  private Set<String> femaleNames;

  public static TestGenderModel testModel(String name) throws IOException {
    GenderModel gm = new GenderModel(name, false);
    return gm;
  }

  public static TrainSimilarityModel trainModel(String name) throws IOException {
    GenderModel gm = new GenderModel(name, true);
    return gm;
  }

  private Set<String> readNames(String nameFile) throws IOException {
    Set<String> names = new HashSet<String>();
    BufferedReader nameReader = new BufferedReader(new FileReader(nameFile));
    for (String line = nameReader.readLine(); line != null; line = nameReader.readLine()) {
      names.add(line);
    }
    return names;
  }

  private GenderModel(String modelName, boolean train) throws IOException {
    this.modelName = modelName;
    maleNames = readNames(modelName+".mas");
    femaleNames = readNames(modelName+".fem");
    if (train) {
      events = new ArrayList<Event>();
    }
    else {
      //if (MaxentResolver.loadAsResource()) {
      //  testModel = (new BinaryGISModelReader(new DataInputStream(this.getClass().getResourceAsStream(modelName)))).getModel();
      //}
      testModel = (new SuffixSensitiveGISModelReader(new File(modelName+modelExtension))).getModel();
      maleIndex = testModel.getIndex(GenderEnum.MALE.toString());
      femaleIndex = testModel.getIndex(GenderEnum.FEMALE.toString());
      neuterIndex = testModel.getIndex(GenderEnum.NEUTER.toString());
    }
  }

  private List<String> getFeatures(Context np1) {
    List<String> features = new ArrayList<String>();
    features.add("default");
    for (int ti = 0, tl = np1.getHeadTokenIndex(); ti < tl; ti++) {
      features.add("mw=" + np1.getTokens()[ti].toString());
    }
    features.add("hw=" + np1.getHeadTokenText());
    features.add("n="+np1.getNameType());
    if (np1.getNameType() != null && np1.getNameType().equals("person")) {
      Object[] tokens = np1.getTokens();
      //System.err.println("GenderModel.getFeatures: person name="+np1);
      for (int ti=0;ti<np1.getHeadTokenIndex() || ti==0;ti++) {
        String name = tokens[ti].toString().toLowerCase();
        if (femaleNames.contains(name)) {
          features.add("fem");
          //System.err.println("GenderModel.getFeatures: person (fem) "+np1);
        }
        if (maleNames.contains(name)) {
          features.add("mas");
          //System.err.println("GenderModel.getFeatures: person (mas) "+np1);
        }
      }
    }

    for (String si : np1.getSynsets()) {
      features.add("ss=" + si);
    }
    return features;
  }

  private void addEvent(String outcome, Context np1) {
    List<String> feats = getFeatures(np1);
    events.add(new Event(outcome, feats.toArray(new String[feats.size()])));
  }

  /**
   * Heuristic computation of gender for a mention context using pronouns and honorifics.
   * @param mention The mention whose gender is to be computed.
   * @return The heuristically determined gender or unknown.
   */
  private GenderEnum getGender(Context mention) {
    if (ResolverUtils.malePronounPattern.matcher(mention.getHeadTokenText()).matches()) {
      return GenderEnum.MALE;
    }
    else if (ResolverUtils.femalePronounPattern.matcher(mention.getHeadTokenText()).matches()) {
      return GenderEnum.FEMALE;
    }
    else if (ResolverUtils.neuterPronounPattern.matcher(mention.getHeadTokenText()).matches()) {
      return GenderEnum.NEUTER;
    }
    Object[] mtokens = mention.getTokens();
    for (int ti = 0, tl = mtokens.length - 1; ti < tl; ti++) {
      String token = mtokens[ti].toString();
      if (token.equals("Mr.") || token.equals("Mr")) {
        return GenderEnum.MALE;
      }
      else if (token.equals("Mrs.") || token.equals("Mrs") || token.equals("Ms.") || token.equals("Ms")) {
        return GenderEnum.FEMALE;
      }
    }

    return GenderEnum.UNKNOWN;
  }

  private GenderEnum getGender(List<Context> entity) {
    for (Iterator<Context> ci = entity.iterator(); ci.hasNext();) {
      Context ec = ci.next();
      GenderEnum ge = getGender(ec);
      if (ge != GenderEnum.UNKNOWN) {
        return ge;
      }
    }

    return GenderEnum.UNKNOWN;
  }

  @SuppressWarnings("unchecked")
  public void setExtents(Context[] extentContexts) {
    HashList entities = new HashList();
    List<Context> singletons = new ArrayList<Context>();
    for (int ei = 0, el = extentContexts.length; ei < el; ei++) {
      Context ec = extentContexts[ei];
      //System.err.println("GenderModel.setExtents: ec("+ec.getId()+") "+ec.toText());
      if (ec.getId() != -1) {
        entities.put(ec.getId(), ec);
      }
      else {
        singletons.add(ec);
      }
    }
    List<Context> males = new ArrayList<Context>();
    List<Context> females = new ArrayList<Context>();
    List<Context> eunuches = new ArrayList<Context>();
    //coref entities
    for (Iterator<Integer> ei = entities.keySet().iterator(); ei.hasNext();) {
      Integer key = ei.next();
      List<Context> entityContexts = (List<Context>) entities.get(key);
      GenderEnum gender = getGender(entityContexts);
      if (gender != null) {
        if (gender == GenderEnum.MALE) {
          males.addAll(entityContexts);
        }
        else if (gender == GenderEnum.FEMALE) {
          females.addAll(entityContexts);
        }
        else if (gender == GenderEnum.NEUTER) {
          eunuches.addAll(entityContexts);
        }
      }
    }
    //non-coref entities
    for (Iterator<Context> ei = singletons.iterator(); ei.hasNext();) {
      Context ec = ei.next();
      GenderEnum gender = getGender(ec);
      if (gender == GenderEnum.MALE) {
        males.add(ec);
      }
      else if (gender == GenderEnum.FEMALE) {
        females.add(ec);
      }
      else if (gender == GenderEnum.NEUTER) {
        eunuches.add(ec);
      }
    }
    for (Iterator<Context> mi = males.iterator(); mi.hasNext();) {
      Context ec = mi.next();
      addEvent(GenderEnum.MALE.toString(), ec);
    }
    for (Iterator<Context> fi = females.iterator(); fi.hasNext();) {
      Context ec = fi.next();
      addEvent(GenderEnum.FEMALE.toString(), ec);
    }
    for (Iterator<Context> ei = eunuches.iterator(); ei.hasNext();) {
      Context ec = ei.next();
      addEvent(GenderEnum.NEUTER.toString(), ec);
    }
  }

  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      System.err.println("Usage: GenderModel modelName < tiger/NN bear/NN");
      System.exit(1);
    }
    String modelName = args[0];
    GenderModel model = new GenderModel(modelName, false);
    //Context.wn = new WordNet(System.getProperty("WNHOME"), true);
    //Context.morphy = new Morphy(Context.wn);
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    for (String line = in.readLine(); line != null; line = in.readLine()) {
      String[] words = line.split(" ");
      double[] dist = model.genderDistribution(Context.parseContext(words[0]));
      System.out.println("m="+dist[model.getMaleIndex()] + " f=" +dist[model.getFemaleIndex()]+" n="+dist[model.getNeuterIndex()]+" "+model.getFeatures(Context.parseContext(words[0])));
    }
  }

  public double[] genderDistribution(Context np1) {
    List<String> features = getFeatures(np1);
    if (debugOn) {
      //System.err.println("GenderModel.genderDistribution: "+features);
    }
    return testModel.eval(features.toArray(new String[features.size()]));
  }

  public void trainModel() throws IOException {
    if (debugOn) {
      FileWriter writer = new FileWriter(modelName+".events");
      for (Iterator<Event> ei=events.iterator();ei.hasNext();) {
        Event e = ei.next();
        writer.write(e.toString()+"\n");
      }
      writer.close();
    }
    new SuffixSensitiveGISModelWriter(
        GIS.trainModel(
        new CollectionEventStream(events), true),
        new File(modelName+modelExtension)).persist();
  }

  public int getFemaleIndex() {
    return femaleIndex;
  }

  public int getMaleIndex() {
    return maleIndex;
  }

  public int getNeuterIndex() {
    return neuterIndex;
  }
}
