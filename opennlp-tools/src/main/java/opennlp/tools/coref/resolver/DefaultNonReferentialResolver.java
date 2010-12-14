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

import java.io.DataInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import opennlp.maxent.GIS;
import opennlp.maxent.io.BinaryGISModelReader;
import opennlp.maxent.io.SuffixSensitiveGISModelReader;
import opennlp.maxent.io.SuffixSensitiveGISModelWriter;
import opennlp.model.Event;
import opennlp.model.MaxentModel;
import opennlp.tools.coref.mention.MentionContext;
import opennlp.tools.coref.mention.Parse;
import opennlp.tools.util.CollectionEventStream;

/**
 * Default implementation of the {@link NonReferentialResolver} interface.
 */
public class DefaultNonReferentialResolver implements NonReferentialResolver {

  private MaxentModel model;
  private List<Event> events;
  private boolean loadAsResource;
  private boolean debugOn = false;
  private ResolverMode mode;
  private String modelName;
  private String modelExtension = ".bin.gz";
  private int nonRefIndex;

  public DefaultNonReferentialResolver(String projectName, String name, ResolverMode mode) throws IOException {
    this.mode = mode;
    this.modelName = projectName+"/"+name+".nr";
    if (mode == ResolverMode.TRAIN) {
      events = new ArrayList<Event>();
    }
    else if (mode == ResolverMode.TEST) {
      if (loadAsResource) {
        model = (new BinaryGISModelReader(new DataInputStream(this.getClass().getResourceAsStream(modelName)))).getModel();
      }
      else {
        model = (new SuffixSensitiveGISModelReader(new File(modelName+modelExtension))).getModel();
      }
      nonRefIndex = model.getIndex(MaxentResolver.SAME);
    }
    else {
      throw new RuntimeException("unexpected mode "+mode);
    }
  }

  public double getNonReferentialProbability(MentionContext mention) {
    List<String> features = getFeatures(mention);
    double r = model.eval(features.toArray(new String[features.size()]))[nonRefIndex];
    if (debugOn) System.err.println(this +" " + mention.toText() + " ->  null " + r + " " + features);
    return r;
  }

  public void addEvent(MentionContext ec) {
    List<String> features = getFeatures(ec);
    if (-1 == ec.getId()) {
      events.add(new Event(MaxentResolver.SAME, features.toArray(new String[features.size()])));
    }
    else {
      events.add(new Event(MaxentResolver.DIFF, features.toArray(new String[features.size()])));
    }
  }

  protected List<String> getFeatures(MentionContext mention) {
    List<String> features = new ArrayList<String>();
    features.add(MaxentResolver.DEFAULT);
    features.addAll(getNonReferentialFeatures(mention));
    return features;
  }

  /**
   * Returns a list of features used to predict whether the specified mention is non-referential.
   * @param mention The mention under consideration.
   * @return a list of features used to predict whether the specified mention is non-referential.
   */
  protected List<String> getNonReferentialFeatures(MentionContext mention) {
    List<String> features = new ArrayList<String>();
    Parse[] mtokens = mention.getTokenParses();
    //System.err.println("getNonReferentialFeatures: mention has "+mtokens.length+" tokens");
    for (int ti = 0; ti <= mention.getHeadTokenIndex(); ti++) {
      Parse tok = mtokens[ti];
      List<String> wfs = ResolverUtils.getWordFeatures(tok);
      for (int wfi = 0; wfi < wfs.size(); wfi++) {
        features.add("nr" + wfs.get(wfi));
      }
    }
    features.addAll(ResolverUtils.getContextFeatures(mention));
    return features;
  }

  public void train() throws IOException {
    if (ResolverMode.TRAIN == mode) {
      System.err.println(this +" referential");
      if (debugOn) {
        FileWriter writer = new FileWriter(modelName+".events");
        for (Iterator<Event> ei=events.iterator();ei.hasNext();) {
          Event e = ei.next();
          writer.write(e.toString()+"\n");
        }
        writer.close();
      }
      (new SuffixSensitiveGISModelWriter(GIS.trainModel(new CollectionEventStream(events),100,10),new File(modelName+modelExtension))).persist();
    }
  }
}
