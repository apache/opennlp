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

/**
 * This class provide common utilities for feature generation.
 */
public class FeatureGeneratorUtil {

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
    return FastTokenClassFeatureGenerator.tokenFeature(token);
  }
}
