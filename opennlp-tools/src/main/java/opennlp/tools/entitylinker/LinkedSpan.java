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
 * A default, extended {@link Span} that holds additional information about a {@link Span}.
 *
 * @param <T> The generic type that specializes a {@link BaseLink}.
 */
public class LinkedSpan<T extends BaseLink> extends Span {

  private ArrayList<T> linkedEntries;
  private int sentenceid = 0;
  private String searchTerm;

  /**
   * Initializes a new {@link LinkedSpan}. Sets the prob to {@code 0} as default.
   *
   * @param linkedEntries The {@code n} best linked entries from
   *                      an external data source.
   * @param s The start position of a {@link Span}.
   *          Must be equal to or greater than {@code 0}.
   *          Must not be greater than {@code e}.
   * @param e The end position of a {@link Span}, which is {@code +1}
   *          more than the last element in the span.
   *          Must be equal to or greater than {@code 0}.
   * @param type The type of the span.
   *
   * @throws IllegalArgumentException Thrown if given parameters are invalid.
   */
  public LinkedSpan(ArrayList<T> linkedEntries, int s, int e, String type) {
    this(linkedEntries, s, e, type, 0d);
  }

  /**
   * Initializes a new {@link LinkedSpan}.
   *
   * @param linkedEntries The {@code n} best linked entries from
   *                      an external data source.
   * @param s The start position of a {@link Span}.
   *          Must be equal to or greater than {@code 0}.
   *          Must not be greater than {@code e}.
   * @param e The end position of a {@link Span}, which is {@code +1}
   *          more than the last element in the span.
   *          Must be equal to or greater than {@code 0}.
   * @param type The type of the span.
   * @param prob The probability of the {@link Span}.
   *
   * @throws IllegalArgumentException Thrown if given parameters are invalid.
   */
  public LinkedSpan(ArrayList<T> linkedEntries, int s, int e, String type, double prob) {
    super(s, e, type, prob);
    this.linkedEntries = linkedEntries;
  }

  /**
   * Initializes a new {@link LinkedSpan}.
   *
   * @param linkedEntries The {@code n} best linked entries from
   *                      an external data source.
   * @param s The start position of a {@link Span}.
   *          Must be equal to or greater than {@code 0}.
   *          Must not be greater than {@code e}.
   * @param e The end position of a {@link Span}, which is {@code +1}
   *          more than the last element in the span.
   *          Must be equal to or greater than {@code 0}.
   *
   * @throws IllegalArgumentException Thrown if given parameters are invalid.
   */
  public LinkedSpan(ArrayList<T> linkedEntries, int s, int e) {
    super(s, e);
    this.linkedEntries = linkedEntries;
  }

  /**
   * Initializes a new {@link LinkedSpan} via an existing {@link Span}
   * which is shifted by the specified {@code offset}.
   *
   * @param linkedEntries The {@code n} best linked entries from
   *                      an external data source.
   * @param span The existing {@link Span}.
   * @param offset The positive or negative shift offset.
   *
   * @throws IllegalArgumentException Thrown if given parameters are invalid.
   */
  public LinkedSpan(ArrayList<T> linkedEntries, Span span, int offset) {
    super(span, offset);
    this.linkedEntries = linkedEntries;
  }

  /**
   * @return Retrieves the {@code n} best linked entries from an external data source.
   *         For instance, this will hold gazetteer entries for a search into a geonames
   *         gazetteer.
   */
  public ArrayList<T> getLinkedEntries() {
    return linkedEntries;
  }

  /**
   * @param linkedEntries The {@code n} best linked entries from an external data source.
   *                      For instance, this will hold gazetteer entries for a search
   *                      into a geonames gazetteer.
   */
  public void setLinkedEntries(ArrayList<T> linkedEntries) {
    this.linkedEntries = linkedEntries;
  }

  /**
   * @return Retrieves the id or index of the sentence from which this span was extracted.
   */
  public int getSentenceid() {
    return sentenceid;
  }

  /**
   * @param sentenceid The id or index of the sentence from which this span was extracted.
   */
  public void setSentenceid(int sentenceid) {
    this.sentenceid = sentenceid;
  }

  /**
   * @return Retrieves the search term that was used to link this span to an external data
   *         source.
   */
  public String getSearchTerm() {
    return searchTerm;
  }

  /**
   * @param searchTerm The search term that is used to link this span to an external data
   *                   source.
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

    if (obj instanceof LinkedSpan<?> other) {
      return Objects.equals(this.linkedEntries, other.linkedEntries)
          && this.sentenceid == other.sentenceid
          && Objects.equals(this.searchTerm, other.searchTerm);
    }

    return false;
  }
}
