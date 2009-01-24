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


package opennlp.tools.util.featuregen;

import java.util.regex.Pattern;

/**
 * This class provide common utilities for feature generation.
 */
public class FeatureGeneratorUtil {

  private static Pattern lowercase;
  private static Pattern twoDigits;
  private static Pattern fourDigits;
  private static Pattern containsNumber;
  private static Pattern containsLetter;
  private static Pattern containsHyphens;
  private static Pattern containsBackslash;
  private static Pattern containsComma;
  private static Pattern containsPeriod;
  private static Pattern allCaps;
  private static Pattern capPeriod;
  private static Pattern initialCap;

  static {
    lowercase = Pattern.compile("^[a-z]+$");
    twoDigits = Pattern.compile("^[0-9][0-9]$");
    fourDigits = Pattern.compile("^[0-9][0-9][0-9][0-9]$");
    containsNumber = Pattern.compile("[0-9]");
    containsLetter = Pattern.compile("[a-zA-Z]");
    containsHyphens = Pattern.compile("-");
    containsBackslash = Pattern.compile("/");
    containsComma = Pattern.compile(",");
    containsPeriod = Pattern.compile("\\.");
    allCaps = Pattern.compile("^[A-Z]+$");
    capPeriod = Pattern.compile("^[A-Z]\\.$");
    initialCap = Pattern.compile("^[A-Z]");
  }
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
    
    String feat;
    if (lowercase.matcher(token).find()) {
      feat = "lc";
    }
    else if (twoDigits.matcher(token).find()) {
      feat = "2d";
    }
    else if (fourDigits.matcher(token).find()) {
      feat = "4d";
    }
    else if (containsNumber.matcher(token).find()) {
      if (containsLetter.matcher(token).find()) {
        feat = "an";
      }
      else if (containsHyphens.matcher(token).find()) {
        feat = "dd";
      }
      else if (containsBackslash.matcher(token).find()) {
        feat = "ds";
      }
      else if (containsComma.matcher(token).find()) {
        feat = "dc";
      }
      else if (containsPeriod.matcher(token).find()) {
        feat = "dp";
      }
      else {
        feat = "num";
      }
    }
    else if (allCaps.matcher(token).find() && token.length() == 1) {
      feat = "sc";
    }
    else if (allCaps.matcher(token).find()) {
      feat = "ac";
    }
    else if (capPeriod.matcher(token).find()) {
      feat = "cp";
    }
    else if (initialCap.matcher(token).find()) {
      feat = "ic";
    }
    else {
      feat = "other";
    }
    
    return (feat);
  }
}
