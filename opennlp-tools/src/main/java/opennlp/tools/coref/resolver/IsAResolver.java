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
import java.util.regex.Pattern;

import opennlp.tools.coref.DiscourseEntity;
import opennlp.tools.coref.mention.MentionContext;

/**
 *  Resolves coreference between appositives.
 */
public class IsAResolver extends MaxentResolver {

  Pattern predicativePattern;

  public IsAResolver(String projectName, ResolverMode m) throws IOException {
    super(projectName, "/imodel", m, 20);
    showExclusions = false;
    //predicativePattern = Pattern.compile("^(,|am|are|is|was|were|--)$");
    predicativePattern = Pattern.compile("^(,|--)$");
  }

  public IsAResolver(String projectName, ResolverMode m, NonReferentialResolver nrr) throws IOException {
    super(projectName, "/imodel", m, 20,nrr);
    showExclusions = false;
    //predicativePattern = Pattern.compile("^(,|am|are|is|was|were|--)$");
    predicativePattern = Pattern.compile("^(,|--)$");
  }


  public boolean canResolve(MentionContext ec) {
    if (ec.getHeadTokenTag().startsWith("NN")) {
      return (ec.getPreviousToken() != null && predicativePattern.matcher(ec.getPreviousToken().toString()).matches());
    }
    return false;
  }

  @Override
  protected boolean excluded(MentionContext ec, DiscourseEntity de) {
    MentionContext cec = de.getLastExtent();
    //System.err.println("IsAResolver.excluded?: ec.span="+ec.getSpan()+" cec.span="+cec.getSpan()+" cec="+cec.toText()+" lastToken="+ec.getNextToken());
    if (ec.getSentenceNumber() != cec.getSentenceNumber()) {
      //System.err.println("IsAResolver.excluded: (true) not same sentence");
      return (true);
    }
    //shallow parse appositives
    //System.err.println("IsAResolver.excluded: ec="+ec.toText()+" "+ec.span+" cec="+cec.toText()+" "+cec.span);
    if (cec.getIndexSpan().getEnd() == ec.getIndexSpan().getStart() - 2) {
      return (false);
    }
    //full parse w/o trailing comma
    if (cec.getIndexSpan().getEnd() == ec.getIndexSpan().getEnd()) {
      //System.err.println("IsAResolver.excluded: (false) spans share end");
      return (false);
    }
    //full parse w/ trailing comma or period
    if (cec.getIndexSpan().getEnd() <= ec.getIndexSpan().getEnd() + 2 && (ec.getNextToken() != null && (ec.getNextToken().toString().equals(",") || ec.getNextToken().toString().equals(".")))) {
      //System.err.println("IsAResolver.excluded: (false) spans end + punct");
      return (false);
    }
    //System.err.println("IsAResolver.excluded: (true) default");
    return (true);
  }

  @Override
  protected boolean outOfRange(MentionContext ec, DiscourseEntity de) {
    MentionContext cec = de.getLastExtent();
    return (cec.getSentenceNumber() != ec.getSentenceNumber());
  }

  @Override
  protected boolean defaultReferent(DiscourseEntity de) {
    return (true);
  }

  @Override
  protected List<String> getFeatures(MentionContext mention, DiscourseEntity entity) {
    List<String> features = new ArrayList<String>();
    features.addAll(super.getFeatures(mention, entity));
    if (entity != null) {
      MentionContext ant = entity.getLastExtent();
      List<String> leftContexts = ResolverUtils.getContextFeatures(ant);
      for (int ci = 0, cn = leftContexts.size(); ci < cn; ci++) {
        features.add("l" + leftContexts.get(ci));
      }
      List<String> rightContexts = ResolverUtils.getContextFeatures(mention);
      for (int ci = 0, cn = rightContexts.size(); ci < cn; ci++) {
        features.add("r" + rightContexts.get(ci));
      }
      features.add("hts"+ant.getHeadTokenTag()+","+mention.getHeadTokenTag());
    }
    /*
    if (entity != null) {
      //System.err.println("MaxentIsResolver.getFeatures: ["+ec2.toText()+"] -> ["+de.getLastExtent().toText()+"]");
      //previous word and tag
      if (ant.prevToken != null) {
        features.add("pw=" + ant.prevToken);
        features.add("pt=" + ant.prevToken.getSyntacticType());
      }
      else {
        features.add("pw=<none>");
        features.add("pt=<none>");
      }

      //next word and tag
      if (mention.nextToken != null) {
        features.add("nw=" + mention.nextToken);
        features.add("nt=" + mention.nextToken.getSyntacticType());
      }
      else {
        features.add("nw=<none>");
        features.add("nt=<none>");
      }

      //modifier word and tag for c1
      int i = 0;
      List c1toks = ant.tokens;
      for (; i < ant.headTokenIndex; i++) {
        features.add("mw=" + c1toks.get(i));
        features.add("mt=" + ((Parse) c1toks.get(i)).getSyntacticType());
      }
      //head word and tag for c1
      features.add("mh=" + c1toks.get(i));
      features.add("mt=" + ((Parse) c1toks.get(i)).getSyntacticType());

      //modifier word and tag for c2
      i = 0;
      List c2toks = mention.tokens;
      for (; i < mention.headTokenIndex; i++) {
        features.add("mw=" + c2toks.get(i));
        features.add("mt=" + ((Parse) c2toks.get(i)).getSyntacticType());
      }
      //head word and tag for n2
      features.add("mh=" + c2toks.get(i));
      features.add("mt=" + ((Parse) c2toks.get(i)).getSyntacticType());

      //word/tag pairs
      for (i = 0; i < ant.headTokenIndex; i++) {
        for (int j = 0; j < mention.headTokenIndex; j++) {
          features.add("w=" + c1toks.get(i) + "|" + "w=" + c2toks.get(j));
          features.add("w=" + c1toks.get(i) + "|" + "t=" + ((Parse) c2toks.get(j)).getSyntacticType());
          features.add("t=" + ((Parse) c1toks.get(i)).getSyntacticType() + "|" + "w=" + c2toks.get(j));
          features.add("t=" + ((Parse) c1toks.get(i)).getSyntacticType() + "|" + "t=" + ((Parse) c2toks.get(j)).getSyntacticType());
        }
      }
      features.add("ht=" + ant.headTokenTag + "|" + "ht=" + mention.headTokenTag);
      features.add("ht1=" + ant.headTokenTag);
      features.add("ht2=" + mention.headTokenTag);
     */
      //semantic categories
      /*
      if (ant.neType != null) {
        if (re.neType != null) {
          features.add("sc="+ant.neType+","+re.neType);
        }
        else if (!re.headTokenTag.startsWith("NNP") && re.headTokenTag.startsWith("NN")) {
          Set synsets = re.synsets;
          for (Iterator si=synsets.iterator();si.hasNext();) {
            features.add("sc="+ant.neType+","+si.next());
          }
        }
      }
      else if (!ant.headTokenTag.startsWith("NNP") && ant.headTokenTag.startsWith("NN")) {
        if (re.neType != null) {
          Set synsets = ant.synsets;
          for (Iterator si=synsets.iterator();si.hasNext();) {
            features.add("sc="+re.neType+","+si.next());
          }
        }
        else if (!re.headTokenTag.startsWith("NNP") && re.headTokenTag.startsWith("NN")) {
          //System.err.println("MaxentIsaResolover.getFeatures: both common re="+re.parse+" ant="+ant.parse);
          Set synsets1 = ant.synsets;
          Set synsets2 = re.synsets;
          for (Iterator si=synsets1.iterator();si.hasNext();) {
            Object synset = si.next();
            if (synsets2.contains(synset)) {
              features.add("sc="+synset);
            }
          }
        }
      }
    }
    */
    //System.err.println("MaxentIsResolver.getFeatures: "+features.toString());
    return (features);
  }
}
