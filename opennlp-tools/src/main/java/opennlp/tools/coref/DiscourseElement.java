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
import opennlp.tools.util.ReverseListIterator;

/**
 * Represents an item in which can be put into the discourse model.  Object which are
 * to be placed in the discourse model should extend this class.
 *
 * @see opennlp.tools.coref.DiscourseModel
 */
public abstract class DiscourseElement {

  private List<MentionContext> extents;
  private int id=-1;
  private MentionContext lastExtent;

  /**
   * Creates a new discourse element which contains the specified mention.
   *
   * @param mention The mention which begins this discourse element.
   */
  public DiscourseElement(MentionContext mention) {
    extents = new ArrayList<MentionContext>(1);
    lastExtent = mention;
    extents.add(mention);
  }

  /**
   * Returns an iterator over the mentions which iterates through them based on which were most recently mentioned.
   * @return the {@link Iterator}.
   */
  public Iterator<MentionContext> getRecentMentions() {
    return(new ReverseListIterator<MentionContext>(extents));
  }

  /**
   * Returns an iterator over the mentions which iterates through them based on
   * their occurrence in the document.
   *
   * @return the {@link Iterator}
   */
  public Iterator<MentionContext> getMentions() {
    return(extents.listIterator());
  }

  /**
   * Returns the number of mentions in this element.
   *
   * @return number of mentions
   */
  public int getNumMentions() {
    return(extents.size());
  }

  /**
   * Adds the specified mention to this discourse element.
   * @param mention The mention to be added.
   */
  public void addMention(MentionContext mention) {
    extents.add(mention);
    lastExtent=mention;
  }

  /**
   * Returns the last mention for this element.  For appositives this will be the
   * first part of the appositive.
   * @return the last mention for this element.
   */
  public MentionContext getLastExtent() {
    return(lastExtent);
  }

  /**
   * Associates an id with this element.
   * @param id The id.
   */
  public void setId(int id) {
    this.id=id;
  }

  /**
   * Returns the id associated with this element.
   *
   * @return the id associated with this element.
   */
  public int getId() {
    return(id);
  }

  @Override
  public String toString() {
    Iterator<MentionContext> ei = extents.iterator();
    MentionContext ex = ei.next();
    StringBuffer de = new StringBuffer();
    de.append("[ ").append(ex.toText());//.append("<").append(ex.getHeadText()).append(">");
    while (ei.hasNext()) {
      ex = ei.next();
      de.append(", ").append(ex.toText());//.append("<").append(ex.getHeadText()).append(">");
    }
    de.append(" ]");
    return(de.toString());
  }
}
