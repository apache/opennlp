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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.coref.DiscourseEntity;
import opennlp.tools.coref.mention.MentionContext;

/**
 * Resolves pronouns specific to quoted speech such as "you", "me", and "I".
 */
public class SpeechPronounResolver extends MaxentResolver {

  public SpeechPronounResolver(String projectName, ResolverMode m) throws IOException {
    super(projectName,"fmodel", m, 30);
    this.numSentencesBack = 0;
    showExclusions = false;
    preferFirstReferent = true;
  }

  public SpeechPronounResolver(String projectName, ResolverMode m, NonReferentialResolver nrr) throws IOException {
    super(projectName,"fmodel", m, 30,nrr);
    showExclusions = false;
    preferFirstReferent = true;
  }


  @Override
  protected List<String> getFeatures(MentionContext mention, DiscourseEntity entity) {
    List<String> features = new ArrayList<String>();
    features.addAll(super.getFeatures(mention, entity));
    if (entity != null) {
      features.addAll(ResolverUtils.getPronounMatchFeatures(mention,entity));
      List<String> contexts = ResolverUtils.getContextFeatures(mention);
      MentionContext cec = entity.getLastExtent();
      if (mention.getHeadTokenTag().startsWith("PRP") && cec.getHeadTokenTag().startsWith("PRP")) {
        features.add(mention.getHeadTokenText() + "," + cec.getHeadTokenText());
      }
      else if (mention.getHeadTokenText().startsWith("NNP")) {
        for (int ci = 0, cl = contexts.size(); ci < cl; ci++) {
          features.add(contexts.get(ci));
        }
        features.add(mention.getNameType() + "," + cec.getHeadTokenText());
      }
      else {
        List<String> ccontexts = ResolverUtils.getContextFeatures(cec);
        for (int ci = 0, cl = ccontexts.size(); ci < cl; ci++) {
          features.add(ccontexts.get(ci));
        }
        features.add(cec.getNameType() + "," + mention.getHeadTokenText());
      }
    }
    return (features);
  }

  @Override
  protected boolean outOfRange(MentionContext mention, DiscourseEntity entity) {
    MentionContext cec = entity.getLastExtent();
    return (mention.getSentenceNumber() - cec.getSentenceNumber() > numSentencesBack);
  }

  public boolean canResolve(MentionContext mention) {
    String tag = mention.getHeadTokenTag();
    boolean fpp = tag != null && tag.startsWith("PRP") && ResolverUtils.speechPronounPattern.matcher(mention.getHeadTokenText()).matches();
    boolean pn = tag != null && tag.startsWith("NNP");
    return (fpp || pn);
  }

  @Override
  protected boolean excluded(MentionContext mention, DiscourseEntity entity) {
    if (super.excluded(mention, entity)) {
      return true;
    }
    MentionContext cec = entity.getLastExtent();
    if (!canResolve(cec)) {
      return true;
    }
    if (mention.getHeadTokenTag().startsWith("NNP")) { //mention is a propernoun
      if (cec.getHeadTokenTag().startsWith("NNP")) {
        return true; // both NNP
      }
      else {
        if (entity.getNumMentions() > 1) {
          return true;
        }
        return !canResolve(cec);
      }
    }
    else if (mention.getHeadTokenTag().startsWith("PRP")){ // mention is a speech pronoun
      // cec can be either a speech pronoun or a propernoun
      if (cec.getHeadTokenTag().startsWith("NNP")) {
        //exclude antecedents not in the same sentence when they are not pronoun
        return (mention.getSentenceNumber() - cec.getSentenceNumber() != 0);
      }
      else if (cec.getHeadTokenTag().startsWith("PRP")){
        return false;
      }
      else {
        System.err.println("Unexpected candidate exluded: "+cec.toText());
        return true;
      }
    }
    else {
      System.err.println("Unexpected mention exluded: "+mention.toText());
      return true;
    }
  }

}
