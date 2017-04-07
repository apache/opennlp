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


package opennlp.tools.util.featuregen;

import java.util.regex.Pattern;

/**
 * This class provide common utilities for feature generation.
 */
public class FeatureGeneratorUtil {

  private static final String TOKEN_CLASS_PREFIX = "wc";
  private static final String TOKEN_AND_CLASS_PREFIX = "w&c";

  private static final Pattern capPeriod = Pattern.compile("^[A-Z]\\.$");

  /**
   * Generates a class name for the specified token.
   * The classes are as follows where the first matching class is used:
   * <ul>
   * <li>lc - lowercase alphabetic</li>
   * <li>2d - two digits </li>
   * <li>4d - four digits </li>
   * <li>an - alpha-numeric </li>
   * <li>dd - digits and dashes </li>
   * <li>ds - digits and slashes </li>
   * <li>dc - digits and commas </li>
   * <li>dp - digits and periods </li>
   * <li>num - digits </li>
   * <li>sc - single capital letter </li>
   * <li>ac - all capital letters </li>
   * <li>ic - initial capital letter </li>
   * <li>other - other </li>
   * </ul>
   * @param token A token or word.
   * @return The class name that the specified token belongs in.
   */
  public static String tokenFeature(String token) {

    StringPattern pattern = StringPattern.recognize(token);

    String feat;
    if (pattern.isAllLowerCaseLetter()) {
      feat = "lc";
    }
    else if (pattern.digits() == 2) {
      feat = "2d";
    }
    else if (pattern.digits() == 4) {
      feat = "4d";
    }
    else if (pattern.containsDigit()) {
      if (pattern.containsLetters()) {
        feat = "an";
      }
      else if (pattern.containsHyphen()) {
        feat = "dd";
      }
      else if (pattern.containsSlash()) {
        feat = "ds";
      }
      else if (pattern.containsComma()) {
        feat = "dc";
      }
      else if (pattern.containsPeriod()) {
        feat = "dp";
      }
      else {
        feat = "num";
      }
    }
    else if (pattern.isAllCapitalLetter()) {
      if (token.length() == 1) {
        feat = "sc";
      }
      else {
        feat = "ac";
      }
    }
    else if (capPeriod.matcher(token).find()) {
      feat = "cp";
    }
    else if (pattern.isInitialCapitalLetter()) {
      feat = "ic";
    }
    else {
      feat = "other";
    }

    return (feat);
  }
}
