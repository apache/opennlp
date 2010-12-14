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

package opennlp.tools.coref;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import opennlp.tools.coref.mention.MentionContext;

/**
 * Represents the elements which are part of a discourse.
 */
public class DiscourseModel {

  private List<DiscourseEntity> entities;

  int nextEntityId = 1;

  /**
   * Creates a new discourse model.
   */
  public DiscourseModel() {
    entities = new ArrayList<DiscourseEntity>();
  }

  /**
   * Indicates that the specified entity has been mentioned.
   * 
   * @param e The entity which has been mentioned.
   */
  public void mentionEntity(DiscourseEntity e) {
    if (entities.remove(e)) {
      entities.add(0,e);
    }
    else {
      System.err.println("DiscourseModel.mentionEntity: failed to remove "+e);
    }
  }

  /**
   * Returns the number of entities in this discourse model.
   * 
   * @return the number of entities in this discourse model.
   */
  public int getNumEntities() {
    return entities.size();
  }

  /**
   * Returns the entity at the specified index.
   * 
   * @param i The index of the entity to be returned.
   * @return the entity at the specified index.
   */
  public DiscourseEntity getEntity(int i) {
    return entities.get(i);
  }

  /**
   * Adds the specified entity to this discourse model.
   * 
   * @param e the entity to be added to the model.
   */
  public void addEntity(DiscourseEntity e) {
    e.setId(nextEntityId);
    nextEntityId++;
    entities.add(0,e);
  }

  /**
   * Merges the specified entities into a single entity with the specified confidence.
   * 
   * @param e1 The first entity.
   * @param e2 The second entity.
   * @param confidence The confidence.
   */
  public void mergeEntities(DiscourseEntity e1,DiscourseEntity e2,float confidence) {
    for (Iterator<MentionContext> ei=e2.getMentions();ei.hasNext();) {
      e1.addMention(ei.next());
    }
    //System.err.println("DiscourseModel.mergeEntities: removing "+e2);
    entities.remove(e2);
  }

  /**
   * Returns the entities in the discourse model.
   * 
   * @return the entities in the discourse model.
   */
  public DiscourseEntity[] getEntities() {
    DiscourseEntity[] des = new DiscourseEntity[entities.size()];
    entities.toArray(des);
    return des;
  }

  /**
   * Removes all elements from this discourse model.
   */
  public void clear() {
    entities.clear();
  }
}
