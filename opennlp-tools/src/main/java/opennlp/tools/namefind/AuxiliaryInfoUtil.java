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

package opennlp.tools.namefind;

/**
 * If a token contains an auxiliary information, e.g. POS tag, this class can be used
 * to extract word part or auxiliary information part.<br>
 *
 * ex) token := word '/' POStag
 *
 * <strong>EXPERIMENTAL</strong>.
 * This class has been added as part of a work in progress and might change without notice.
 */
public class AuxiliaryInfoUtil {

  public static int getSeparatorIndex(String wordAndAux) {
    int idx = wordAndAux.lastIndexOf('/');
    if (idx < 0)
      throw new RuntimeException(String.format("token %s doesn't have Auxiliary info", wordAndAux));
    return idx;
  }

  public static String getWordPart(String wordAndAux) {
    int idx = getSeparatorIndex(wordAndAux);
    return idx == 0 ? " " : wordAndAux.substring(0, idx);
  }

  public static String[] getWordParts(String[] wordAndAuxes) {
    String[] results = new String[wordAndAuxes.length];
    for (int i = 0; i < wordAndAuxes.length; i++) {
      results[i] = getWordPart(wordAndAuxes[i]);
    }
    return results;
  }

  public static String getAuxPart(String wordAndAux) {
    int idx = getSeparatorIndex(wordAndAux);
    return wordAndAux.substring(idx + 1);
  }

  public static String[] getAuxParts(String[] wordAndAuxes) {
    String[] results = new String[wordAndAuxes.length];
    for (int i = 0; i < wordAndAuxes.length; i++) {
      results[i] = getAuxPart(wordAndAuxes[i]);
    }
    return results;
  }
}
