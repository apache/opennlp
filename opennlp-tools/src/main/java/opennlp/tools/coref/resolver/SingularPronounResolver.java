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
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import opennlp.tools.coref.DiscourseEntity;
import opennlp.tools.coref.mention.MentionContext;

/**
 * This class resolver singular pronouns such as "he", "she", "it" and their various forms.
 */
public class SingularPronounResolver extends MaxentResolver {

  int mode;

  Pattern PronounPattern;

  public SingularPronounResolver(String projectName, ResolverMode m) throws IOException {
    super(projectName, "pmodel", m, 30);
    this.numSentencesBack = 2;
  }

  public SingularPronounResolver(String projectName, ResolverMode m, NonReferentialResolver nonReferentialResolver) throws IOException {
    super(projectName, "pmodel", m, 30,nonReferentialResolver);
    this.numSentencesBack = 2;
  }

  public boolean canResolve(MentionContext mention) {
    //System.err.println("MaxentSingularPronounResolver.canResolve: ec= ("+mention.id+") "+ mention.toText());
    String tag = mention.getHeadTokenTag();
    return (tag != null && tag.startsWith("PRP") && ResolverUtils.singularThirdPersonPronounPattern.matcher(mention.getHeadTokenText()).matches());
  }

  @Override
  protected List<String> getFeatures(MentionContext mention, DiscourseEntity entity) {
    List<String> features = new ArrayList<String>();
    features.addAll(super.getFeatures(mention, entity));
    if (entity != null) { //generate pronoun w/ referent features
      MentionContext cec = entity.getLastExtent();
      //String gen = getPronounGender(pronoun);
      features.addAll(ResolverUtils.getPronounMatchFeatures(mention,entity));
      features.addAll(ResolverUtils.getContextFeatures(cec));
      features.addAll(ResolverUtils.getDistanceFeatures(mention,entity));
      features.add(ResolverUtils.getMentionCountFeature(entity));
      /*
      //lexical features
      Set featureSet = new HashSet();
      for (Iterator ei = entity.getExtents(); ei.hasNext();) {
        MentionContext ec = (MentionContext) ei.next();
        List toks = ec.tokens;
        Parse tok;
        int headIndex = PTBHeadFinder.getInstance().getHeadIndex(toks);
        for (int ti = 0; ti < headIndex; ti++) {
          tok = (Parse) toks.get(ti);
          featureSet.add(gen + "mw=" + tok.toString().toLowerCase());
          featureSet.add(gen + "mt=" + tok.getSyntacticType());
        }
        tok = (Parse) toks.get(headIndex);
        featureSet.add(gen + "hw=" + tok.toString().toLowerCase());
        featureSet.add(gen + "ht=" + tok.getSyntacticType());
        //semantic features
        if (ec.neType != null) {
          featureSet.add(gen + "," + ec.neType);
        }
        else {
          for (Iterator si = ec.synsets.iterator(); si.hasNext();) {
            Integer synset = (Integer) si.next();
            featureSet.add(gen + "," + synset);
          }
        }
      }
      Iterator fset = featureSet.iterator();
      while (fset.hasNext()) {
        String f = (String) fset.next();
        features.add(f);
      }
      */
    }
    return (features);
  }

  @Override
  public boolean excluded(MentionContext mention, DiscourseEntity entity) {
    if (super.excluded(mention, entity)) {
      return (true);
    }
    String mentionGender = null;

    for (Iterator<MentionContext> ei = entity.getMentions(); ei.hasNext();) {
      MentionContext entityMention = ei.next();
      String tag = entityMention.getHeadTokenTag();
      if (tag != null && tag.startsWith("PRP") && ResolverUtils.singularThirdPersonPronounPattern.matcher(mention.getHeadTokenText()).matches()) {
        if (mentionGender == null) { //lazy initialization
          mentionGender = ResolverUtils.getPronounGender(mention.getHeadTokenText());
        }
        String entityGender = ResolverUtils.getPronounGender(entityMention.getHeadTokenText());
        if (!entityGender.equals("u") && !mentionGender.equals(entityGender)) {
          return (true);
        }
      }
    }
    return (false);
  }

  @Override
  protected boolean outOfRange(MentionContext mention, DiscourseEntity entity) {
    MentionContext cec = entity.getLastExtent();
    //System.err.println("MaxentSingularPronounresolve.outOfRange: ["+entity.getLastExtent().toText()+" ("+entity.getId()+")] ["+mention.toText()+" ("+mention.getId()+")] entity.sentenceNumber=("+entity.getLastExtent().getSentenceNumber()+")-mention.sentenceNumber=("+mention.getSentenceNumber()+") > "+numSentencesBack);
    return (mention.getSentenceNumber() - cec.getSentenceNumber() > numSentencesBack);
  }
}
