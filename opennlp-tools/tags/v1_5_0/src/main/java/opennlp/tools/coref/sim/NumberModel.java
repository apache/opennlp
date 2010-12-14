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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import opennlp.maxent.GIS;
import opennlp.maxent.io.SuffixSensitiveGISModelReader;
import opennlp.maxent.io.SuffixSensitiveGISModelWriter;
import opennlp.model.Event;
import opennlp.model.MaxentModel;
import opennlp.tools.coref.resolver.ResolverUtils;
import opennlp.tools.util.CollectionEventStream;
import opennlp.tools.util.HashList;

/**
 * Class which models the number of particular mentions and the entities made up of mentions.
 */
public class NumberModel implements TestNumberModel, TrainSimilarityModel {

  private String modelName;
  private String modelExtension = ".bin.gz";
  private MaxentModel testModel;
  private List<Event> events;

  private int singularIndex;
  private int pluralIndex;

  public static TestNumberModel testModel(String name) throws IOException {
    NumberModel nm = new NumberModel(name, false);
    return nm;
  }

  public static TrainSimilarityModel trainModel(String modelName) throws IOException {
    NumberModel gm = new NumberModel(modelName, true);
    return gm;
  }

  private NumberModel(String modelName, boolean train) throws IOException {
    this.modelName = modelName;
    if (train) {
      events = new ArrayList<Event>();
    }
    else {
      //if (MaxentResolver.loadAsResource()) {
      //  testModel = (new PlainTextGISModelReader(new BufferedReader(new InputStreamReader(this.getClass().getResourceAsStream(modelName))))).getModel();
      //}
      testModel = (new SuffixSensitiveGISModelReader(new File(modelName+modelExtension))).getModel();
      singularIndex = testModel.getIndex(NumberEnum.SINGULAR.toString());
      pluralIndex = testModel.getIndex(NumberEnum.PLURAL.toString());
    }
  }

  private List<String> getFeatures(Context np1) {
    List<String> features = new ArrayList<String>();
    features.add("default");
    Object[] npTokens = np1.getTokens();
    for (int ti = 0, tl = npTokens.length - 1; ti < tl; ti++) {
      features.add("mw=" + npTokens[ti].toString());
    }
    features.add("hw=" + np1.getHeadTokenText().toLowerCase());
    features.add("ht=" + np1.getHeadTokenTag());
    return features;
  }

  private void addEvent(String outcome, Context np1) {
    List<String> feats = getFeatures(np1);
    events.add(new Event(outcome, feats.toArray(new String[feats.size()])));
  }

  public NumberEnum getNumber(Context ec) {
    if (ResolverUtils.singularPronounPattern.matcher(ec.getHeadTokenText()).matches()) {
      return NumberEnum.SINGULAR;
    }
    else if (ResolverUtils.pluralPronounPattern.matcher(ec.getHeadTokenText()).matches()) {
      return NumberEnum.PLURAL;
    }
    else {
      return NumberEnum.UNKNOWN;
    }
  }

  private NumberEnum getNumber(List<Context> entity) {
    for (Iterator<Context> ci = entity.iterator(); ci.hasNext();) {
      Context ec = ci.next();
      NumberEnum ne = getNumber(ec);
      if (ne != NumberEnum.UNKNOWN) {
        return ne;
      }
    }
    return NumberEnum.UNKNOWN;
  }

  @SuppressWarnings("unchecked")
  public void setExtents(Context[] extentContexts) {
    HashList entities = new HashList();
    List<Context> singletons = new ArrayList<Context>();
    for (int ei = 0, el = extentContexts.length; ei < el; ei++) {
      Context ec = extentContexts[ei];
      //System.err.println("NumberModel.setExtents: ec("+ec.getId()+") "+ec.toText());
      if (ec.getId() != -1) {
        entities.put(ec.getId(), ec);
      }
      else {
        singletons.add(ec);
      }
    }
    List<Context> singles = new ArrayList<Context>();
    List<Context> plurals = new ArrayList<Context>();
    // coref entities
    for (Iterator<Integer> ei = entities.keySet().iterator(); ei.hasNext();) {
      Integer key = ei.next();
      List<Context> entityContexts = (List<Context>) entities.get(key);
      NumberEnum number = getNumber(entityContexts);
      if (number == NumberEnum.SINGULAR) {
        singles.addAll(entityContexts);
      }
      else if (number == NumberEnum.PLURAL) {
        plurals.addAll(entityContexts);
      }
    }
    // non-coref entities.
    for (Iterator<Context> ei = singletons.iterator(); ei.hasNext();) {
      Context ec = ei.next();
      NumberEnum number = getNumber(ec);
      if (number == NumberEnum.SINGULAR) {
        singles.add(ec);
      }
      else if (number == NumberEnum.PLURAL) {
        plurals.add(ec);
      }
    }

    for (Iterator<Context> si = singles.iterator(); si.hasNext();) {
      Context ec = si.next();
      addEvent(NumberEnum.SINGULAR.toString(), ec);
    }
    for (Iterator<Context> fi = plurals.iterator(); fi.hasNext();) {
      Context ec = fi.next();
      addEvent(NumberEnum.PLURAL.toString(),ec);
    }
  }

  public double[] numberDist(Context c) {
    List<String> feats = getFeatures(c);
    return testModel.eval(feats.toArray(new String[feats.size()]));
  }

  public int getSingularIndex() {
    return singularIndex;
  }

  public int getPluralIndex() {
    return pluralIndex;
  }

  public void trainModel() throws IOException {
    (new SuffixSensitiveGISModelWriter(GIS.trainModel(new CollectionEventStream(events),100,10),new File(modelName+modelExtension))).persist();
  }

}
