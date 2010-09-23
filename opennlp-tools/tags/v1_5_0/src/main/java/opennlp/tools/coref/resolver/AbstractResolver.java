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

import opennlp.tools.coref.DiscourseEntity;
import opennlp.tools.coref.DiscourseModel;
import opennlp.tools.coref.mention.MentionContext;
import opennlp.tools.coref.mention.Parse;
import opennlp.tools.util.CountedSet;

/**
 * Default implementation of some methods in the {@link Resolver} interface.
 */
public abstract class AbstractResolver implements Resolver {

  /** 
   * The number of previous entities that resolver should consider.
   */
  protected int numEntitiesBack;
  
  /** 
   * Debugging variable which specifies whether error output is generated
   * if a class excludes as possibly coreferent mentions which are in-fact
   * coreferent.
   */
  protected boolean showExclusions;
  
  /** 
   * Debugging variable which holds statistics about mention distances
   * during training.
   */
  protected CountedSet<Integer> distances;
  
  /** 
   * The number of sentences back this resolver should look for a referent.
   */
  protected int numSentencesBack;

  public AbstractResolver(int neb) {
    numEntitiesBack=neb;
    showExclusions = true;
    distances = new CountedSet<Integer>();
  }

  /**
   * Returns the number of previous entities that resolver should consider.
   * 
   * @return the number of previous entities that resolver should consider.
   */
  protected int getNumEntities() {
    return numEntitiesBack;
  }

  /**
   * Specifies the number of sentences back this resolver should look for a referent.
   * 
   * @param nsb the number of sentences back this resolver should look for a referent.
   */
  public void setNumberSentencesBack(int nsb) {
    numSentencesBack = nsb;
  }

  /**
   * The number of entities that should be considered for resolution with the specified discourse model.
   * 
   * @param dm The discourse model.
   * 
   * @return number of entities that should be considered for resolution.
   */
  protected int getNumEntities(DiscourseModel dm) {
    return Math.min(dm.getNumEntities(),numEntitiesBack);
  }

  /**
   * Returns the head parse for the specified mention.
   * 
   * @param mention The mention.
   * 
   * @return the head parse for the specified mention.
   */
  protected Parse getHead(MentionContext mention) {
    return mention.getHeadTokenParse();
  }

  /**
   * Returns the index for the head word for the specified mention.
   * 
   * @param mention The mention.
   * 
   * @return the index for the head word for the specified mention.
   */
  protected int getHeadIndex(MentionContext mention) {
    Parse[] mtokens = mention.getTokenParses();
    for (int ti=mtokens.length-1;ti>=0;ti--) {
      Parse tok = mtokens[ti];
      if (!tok.getSyntacticType().equals("POS") && !tok.getSyntacticType().equals(",") &&
          !tok.getSyntacticType().equals(".")) {
        return ti;
      }
    }
    return mtokens.length-1;
  }

  /**
   * Returns the text of the head word for the specified mention.
   * 
   * @param mention The mention.
   * 
   * @return The text of the head word for the specified mention.
   */
  protected String getHeadString(MentionContext mention) {
    return mention.getHeadTokenText().toLowerCase();
  }

  /**
   * Determines if the specified entity is too far from the specified mention to be resolved to it.
   * Once an entity has been determined to be out of range subsequent entities are not considered.
   * To skip intermediate entities @see excluded.
   * 
   * @param mention The mention which is being considered.
   * @param entity The entity to which the mention is to be resolved.
   * 
   * @return true is the entity is in range of the mention, false otherwise.
   */
  protected boolean outOfRange(MentionContext mention, DiscourseEntity entity) {
    return false;
  }

  /**
   * Excludes entities which you are not compatible with the entity under consideration.  The default
   * implementation excludes entities whose last extent contains the extent under consideration.
   * This prevents possessive pronouns from referring to the noun phrases they modify and other
   * undesirable things.
   * 
   * @param mention The mention which is being considered as referential.
   * @param entity The entity to which the mention is to be resolved.
   * 
   * @return true if the entity should be excluded, false otherwise.
   */
  protected boolean excluded(MentionContext mention, DiscourseEntity entity) {
    MentionContext cec = entity.getLastExtent();
    return mention.getSentenceNumber() == cec.getSentenceNumber() &&
	   mention.getIndexSpan().getEnd() <= cec.getIndexSpan().getEnd();
  }

  public DiscourseEntity retain(MentionContext mention, DiscourseModel dm) {
    int ei = 0;
    if (mention.getId() == -1) {
      return null;
    }
    for (; ei < dm.getNumEntities(); ei++) {
      DiscourseEntity cde = dm.getEntity(ei);
      MentionContext cec = cde.getLastExtent(); // candidate extent context
      if (cec.getId() == mention.getId()) {
        distances.add(new Integer(ei));
        return cde;
      }
    }
    //System.err.println("AbstractResolver.retain: non-refering entity with id: "+ec.toText()+" id="+ec.id);
    return null;
  }

  /**
   * Returns the string of "_" delimited tokens for the specified mention.
   * 
   * @param mention The mention.
   * 
   * @return the string of "_" delimited tokens for the specified mention.
   */
  protected String featureString(MentionContext mention){
    StringBuffer fs = new StringBuffer();
    Object[] mtokens =mention.getTokens();
    fs.append(mtokens[0].toString());
    for (int ti=1,tl=mtokens.length;ti<tl;ti++) {
      fs.append("_").append(mtokens[ti].toString());
    }
    return fs.toString();
  }


  public void train() throws IOException {};
}
