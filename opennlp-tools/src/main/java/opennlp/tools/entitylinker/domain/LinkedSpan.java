/*
 * Copyright 2013 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package opennlp.tools.entitylinker.domain;

import java.util.ArrayList;
import opennlp.tools.util.Span;

/**
 * An "default" extended span that holds additional information about the Span
 *
 *
 */
public class LinkedSpan<T extends BaseLink> extends Span {

  private ArrayList<T> linkedEntries;
  private int sentenceid = 0;
  private String searchTerm;


  
  public LinkedSpan(ArrayList<T> linkedEntries, int s, int e, String type) {
    super(s, e, type);
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

  public ArrayList<T> getLinkedEntries() {
    return linkedEntries;
  }

  public void setLinkedEntries(ArrayList<T> linkedEntries) {
    this.linkedEntries = linkedEntries;
  }

  public int getSentenceid() {
    return sentenceid;
  }

  public void setSentenceid(int sentenceid) {
    this.sentenceid = sentenceid;
  }
  public String getSearchTerm() {
    return searchTerm;
  }

  public void setSearchTerm(String searchTerm) {
    this.searchTerm = searchTerm;
  }
  @Override
  public String toString() {
    return "LinkedSpan{" + "linkedEntries=" + linkedEntries + '}';
  }





  
}