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
package opennlp.tools.entitylinker;

import java.util.ArrayList;
import java.util.Objects;

import opennlp.tools.util.Span;

/**
 * An "default" extended span that holds additional information about the Span
 *
 *
 * @param <T>
 */
public class LinkedSpan<T extends BaseLink> extends Span {

  private ArrayList<T> linkedEntries;
  private int sentenceid = 0;
  private String searchTerm;

  public LinkedSpan(ArrayList<T> linkedEntries, int s, int e, String type) {
    super(s, e, type);
    this.linkedEntries = linkedEntries;
  }

  public LinkedSpan(ArrayList<T> linkedEntries, int s, int e, String type, double prob) {
    super(s, e, type, prob);
    this.linkedEntries = linkedEntries;
  }

  public LinkedSpan(ArrayList<T> linkedEntries, int s, int e) {
    super(s, e);
    this.linkedEntries = linkedEntries;
  }

  public LinkedSpan(ArrayList<T> linkedEntries, Span span, int offset) {
    super(span, offset);
    this.linkedEntries = linkedEntries;
  }

  /**
   * Returns the n best linked entries from an external data source. For
   * instance, this will hold gazateer entries for a search into a geonames
   * gazateer
   *
   * @return
   */
  public ArrayList<T> getLinkedEntries() {
    return linkedEntries;
  }

  /**
   * Sets the n best linked entries from an external data source. For instance,
   * this will hold gazateer entries for a search into a geonames gazateer
   *
   */
  public void setLinkedEntries(ArrayList<T> linkedEntries) {
    this.linkedEntries = linkedEntries;
  }

  /**
   * Returns the id or index of the sentence from which this span was extracted
   *
   * @return
   */
  public int getSentenceid() {
    return sentenceid;
  }

  /**
   * sets the id or index of the sentence from which this span was extracted
   *
   * @param sentenceid
   */
  public void setSentenceid(int sentenceid) {
    this.sentenceid = sentenceid;
  }

  /**
   * Returns the search term that was used to link this span to an external data
   * source
   *
   * @return searchTerm
   */
  public String getSearchTerm() {
    return searchTerm;
  }

  /**
   * sets the search term that is used to link this span to an external data
   * source
   *
   * @param searchTerm
   */
  public void setSearchTerm(String searchTerm) {
    this.searchTerm = searchTerm;
  }

  @Override
  public String toString() {
    return "LinkedSpan\nsentenceid=" + sentenceid + "\nsearchTerm=" + searchTerm
        + "\nlinkedEntries=\n" + linkedEntries + "\n";
  }

  @Override
  public int hashCode() {
    return Objects.hash(linkedEntries, sentenceid, searchTerm);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }

    if (obj instanceof LinkedSpan) {
      final LinkedSpan<T> other = (LinkedSpan<T>) obj;
      return Objects.equals(this.linkedEntries, other.linkedEntries)
          && this.sentenceid == other.sentenceid
          && Objects.equals(this.searchTerm, other.searchTerm);
    }

    return false;
  }
}
