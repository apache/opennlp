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


package opennlp.tools.postag;

import java.util.List;

/**
 * The interface for part of speech taggers.
 *
 * @author      Jason Baldridge
 * @version $Revision: 1.5 $, $Date: 2008-09-28 18:12:22 $ */

public interface POSTagger {

  /** Assigns the sentence of tokens pos tags.
   * @param sentence The sentece of tokens to be tagged.
   * @return a list of pos tags for each token provided in sentence.
   */
  public List<String> tag(List<String> sentence);

  /** Assigns the sentence of tokens pos tags.
   * @param sentence The sentece of tokens to be tagged.
   * @return an array of pos tags for each token provided in sentence.
   */
  public String[] tag(String[] sentence);

  /** Assigns the sentence of space-delimied tokens pos tags.
   * @param sentence The sentece of space-delimited tokens to be tagged.
   * @return a string of space-delimited pos tags for each token provided in sentence.
   */
  public String tag(String sentence);

}
