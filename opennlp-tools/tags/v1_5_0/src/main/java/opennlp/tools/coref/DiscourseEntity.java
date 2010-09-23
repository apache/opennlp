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

import opennlp.tools.coref.mention.MentionContext;
import opennlp.tools.coref.sim.GenderEnum;
import opennlp.tools.coref.sim.NumberEnum;

/**
 * Represents an entity in a discourse model.
 */
public class DiscourseEntity extends DiscourseElement {

  private String category = null;
  private GenderEnum gender;
  private double genderProb;
  private NumberEnum number;
  private double numberProb;

  /**
   * Creates a new entity based on the specified mention and its specified gender and number properties.
   *
   * @param mention The first mention of this entity.
   * @param gender The gender of this entity.
   * @param genderProb The probability that the specified gender is correct.
   * @param number The number for this entity.
   * @param numberProb The probability that the specified number is correct.
   */
  public DiscourseEntity(MentionContext mention, GenderEnum gender, double genderProb, NumberEnum number, double numberProb) {
    super(mention);
    this.gender = gender;
    this.genderProb = genderProb;
    this.number = number;
    this.numberProb = numberProb;
  }

  /**
   * Creates a new entity based on the specified mention.
   *
   * @param mention The first mention of this entity.
   */
  public DiscourseEntity(MentionContext mention) {
    super(mention);
    gender = GenderEnum.UNKNOWN;
    number = NumberEnum.UNKNOWN;
  }

  /**
   * Returns the semantic category of this entity.
   * This field is used to associated named-entity categories with an entity.
   *
   * @return the semantic category of this entity.
   */
  public String getCategory() {
    return (category);
  }

  /**
   * Specifies the semantic category of this entity.
   *
   * @param cat The semantic category of the entity.
   */
  public void setCategory(String cat) {
    category = cat;
  }

  /**
   * Returns the gender associated with this entity.
   *
   * @return the gender associated with this entity.
   */
  public GenderEnum getGender() {
    return gender;
  }

  /**
   * Returns the probability for the gender associated with this entity.
   *
   * @return the probability for the gender associated with this entity.
   */
  public double getGenderProbability() {
    return genderProb;
  }

  /**
   * Returns the number associated with this entity.
   *
   * @return the number associated with this entity.
   */
  public NumberEnum getNumber() {
    return number;
  }

  /**
   * Returns the probability for the number associated with this entity.
   *
   * @return the probability for the number associated with this entity.
   */
  public double getNumberProbability() {
    return numberProb;
  }

  /**
   * Specifies the gender of this entity.
   *
   * @param gender The gender.
   */
  public void setGender(GenderEnum gender) {
    this.gender = gender;
  }

  /**
   * Specifies the probability of the gender of this entity.
   *
   * @param p the probability of the gender of this entity.
   */
  public void setGenderProbability(double p) {
    genderProb = p;
  }

  /**
   * Specifies the number of this entity.
   *
   * @param number
   */
  public void setNumber(NumberEnum number) {
    this.number = number;
  }

  /**
   * Specifies the probability of the number of this entity.
   *
   * @param p the probability of the number of this entity.
   */
  public void setNumberProbability(double p) {
    numberProb = p;
  }
}
