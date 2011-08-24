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

package opennlp.tools.coref.mention;

import opennlp.tools.util.Span;

/**
 * Data structure representation of a mention.
 */
public class Mention implements Comparable<Mention> {

  /** 
   * Represents the character offset for this extent. 
   */
  private Span span;

  /** 
   * A string representing the type of this extent. This is helpful for determining
   * which piece of code created a particular extent.
   */
  protected String type;
  
  /** 
   * The entity id indicating which entity this extent belongs to.  This is only
   * used when training a coreference classifier.
   */
  private int id;

  /** 
   * Represents the character offsets of the the head of this extent.
   */
  private Span headSpan;

  /** 
   * The parse node that this extent is based on.
   */
  protected Parse parse;

  /** 
   * A string representing the name type for this extent. 
   */
  protected String nameType;

  public Mention(Span span, Span headSpan, int entityId, Parse parse, String extentType) {
    this.span=span;
    this.headSpan=headSpan;
    this.id=entityId;
    this.type=extentType;
    this.parse = parse;
  }

  public Mention(Span span, Span headSpan, int entityId, Parse parse, String extentType, String nameType) {
    this.span=span;
    this.headSpan=headSpan;
    this.id=entityId;
    this.type=extentType;
    this.parse = parse;
    this.nameType = nameType;
  }

  public Mention(Mention mention) {
    this(mention.span,mention.headSpan,mention.id,mention.parse,mention.type,mention.nameType);
  }

  /**
   * Returns the character offsets for this extent.
   * 
   * @return The span representing the character offsets of this extent.
   */
  public Span getSpan() {
    return span;
  }

  /**
   * Returns the character offsets for the head of this extent.
   * 
   * @return The span representing the character offsets for the head of this extent.
   */
  public Span getHeadSpan() {
    return headSpan;
  }

  /**
   * Returns the parse node that this extent is based on.
   * 
   * @return The parse node that this extent is based on or null if the extent is newly created.
   */
  public Parse getParse() {
    return parse;
  }

  public int compareTo(Mention e) {
    return span.compareTo(e.span);
  }

  /**
   * Specifies the parse for this mention.
   * @param parse The parse for this mention.
   */
  public void setParse(Parse parse) {
    this.parse = parse;
  }

  /**
   * Returns the named-entity category associated with this mention.
   * 
   * @return the named-entity category associated with this mention.
   */
  public String getNameType() {
    return nameType;
  }

  /**
   * Specifies the named-entity category associated with this mention.
   * 
   * @param nameType the named-entity category associated with this mention.
   */
  protected void setNameType(String nameType) {
    this.nameType = nameType;
  }

  /**
   * Associates an id with this mention.
   * 
   * @param i The id for this mention.
   */
  public void setId(int i) {
    id=i;
  }

  /**
   * Returns the id associated with this mention.
   * 
   * @return the id associated with this mention.
   */
  public int getId() {
    return id;
  }

  @Override
  public String toString() {
    return "mention(span="+span+",hs="+headSpan+", type="+type+", id="+id+" "+parse+" )";
  }
}
