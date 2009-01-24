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

import java.io.IOException;
import java.util.regex.Pattern;

import opennlp.tools.coref.mention.HeadFinder;
import opennlp.tools.coref.mention.Mention;
import opennlp.tools.coref.mention.MentionContext;
import opennlp.tools.coref.mention.MentionFinder;

/** A linker provides an interface for finding mentions, {@link #getMentionFinder getMentionFinder}, 
 * and creating entities out of those mentions, {@link #getEntities getEntities}.  This interface also allows
 * for the training of a resolver with the method {@link #setEntities setEntitites} which is used to give the
 * resolver mentions whose entityId fields indicate which mentions refer to the same entity and the 
 * {@link #train train} method which compiles all the inormation provided via calls to 
 * {@link #setEntities setEntities} into a model.
 * @author Tom Morton
 *
 */
public interface Linker {
  

  /** String constant used to label a mention which is a description. */
  public static final String DESCRIPTOR = "desc";
  /** String constation used to label an mention in an appositive relationship. */
  public static final String ISA = "isa";
  /** String constatant used to label a mention which consists of two or more noun phrases. */
  public static final String COMBINED_NPS = "cmbnd";
  /** String constatant used to label a mention which consists of a single noun phrase. */
  public static final String NP = "np";
  /** String constatant used to label a mention which is a proper noun modifing another noun. */
  public static final String PROPER_NOUN_MODIFIER = "pnmod";
  /** String constatant used to label a mention which is a pronoun. */
  public static final String PRONOUN_MODIFIER = "np";
  
  /** Regular expression for English singular third person pronouns. */
  public static final Pattern singularThirdPersonPronounPattern = Pattern.compile("^(he|she|it|him|her|his|hers|its|himself|herself|itself)$",Pattern.CASE_INSENSITIVE);
  /** Regular expression for English plural third person pronouns. */
  public static final Pattern pluralThirdPersonPronounPattern = Pattern.compile("^(they|their|theirs|them|themselves)$",Pattern.CASE_INSENSITIVE);
  /** Regular expression for English speech pronouns. */
  public static final Pattern speechPronounPattern = Pattern.compile("^(I|me|my|you|your|you|we|us|our|ours)$",Pattern.CASE_INSENSITIVE);
  /** Regular expression for English male pronouns. */
  public static final Pattern malePronounPattern = Pattern.compile("^(he|him|his|himself)$",Pattern.CASE_INSENSITIVE);
  /** Regular expression for English female pronouns. */
  public static final Pattern femalePronounPattern = Pattern.compile("^(she|her|hers|herself)$",Pattern.CASE_INSENSITIVE);
  /** Regular expression for English nueter pronouns. */
  public static final Pattern neuterPronounPattern = Pattern.compile("^(it|its|itself)$",Pattern.CASE_INSENSITIVE);
  /** Regular expression for English first person pronouns. */
  public static final Pattern firstPersonPronounPattern = Pattern.compile("^(I|me|my|we|our|us|ours)$",Pattern.CASE_INSENSITIVE);
  /** Regular expression for English singular second person pronouns. */
  public static final Pattern secondPersonPronounPattern = Pattern.compile("^(you|your|yours)$",Pattern.CASE_INSENSITIVE);
  /** Regular expression for English third person pronouns. */
  public static final Pattern thirdPersonPronounPattern = Pattern.compile("^(he|she|it|him|her|his|hers|its|himself|herself|itself|they|their|theirs|them|themselves)$",Pattern.CASE_INSENSITIVE);
  /** Regular expression for English singular pronouns. */
  public static final Pattern singularPronounPattern = Pattern.compile("^(I|me|my|he|she|it|him|her|his|hers|its|himself|herself|itself)$",Pattern.CASE_INSENSITIVE);
  /** Regular expression for English plural pronouns. */
  public static final Pattern pluralPronounPattern = Pattern.compile("^(we|us|our|ours|they|their|theirs|them|themselves)$",Pattern.CASE_INSENSITIVE);
  /** Regular expression for English honorifics. */
  public static final Pattern honorificsPattern = Pattern.compile("[A-Z][a-z]+\\.$|^[A-Z][b-df-hj-np-tv-xz]+$");
  /** Regular expression for English corporate designators. */
  public static final Pattern designatorsPattern = Pattern.compile("[a-z]\\.$|^[A-Z][b-df-hj-np-tv-xz]+$|^Co(rp)?$");
  
  /**
   * Indicated that the specified mentions can be used to train this linker.
   * This requires that the coreference relationship between the mentions have been labeled
   * in the mention's id field.
   * @param mentions The mentions to be used to train the linker.
   */
  public void setEntities(Mention[] mentions);
  
  /** Returns a list of entities which group the mentions into entity classes. 
   * @param mentions A array of mentions. 
   * @return An array of discourse entities.
   */
  public DiscourseEntity[] getEntities(Mention[] mentions);
  
  /**
   * Creates mention contexts for the specified mention exents.  These are used to compute coreference features over.
   * @param mentions The mention of a document.
   * @return mention contexts for the specified mention exents.
   */
  public MentionContext[] constructMentionContexts(Mention[] mentions);
  
  /** Trains the linker based on the data specified via calls to {@link #setEntities setEntities}. 
   * @throws IOException 
   */ 
  public void train() throws IOException;
    
  /**
   * Returns the mention finder for this linker.  This can be used to get the mentions of a Parse.
   * @return The object which finds mentions for this linker.
   */
  public MentionFinder getMentionFinder();
 
  /** 
   * Returns the head finder associated with this linker.
   * @return The head finder associated with this linker.
   */
  public HeadFinder getHeadFinder();
}
