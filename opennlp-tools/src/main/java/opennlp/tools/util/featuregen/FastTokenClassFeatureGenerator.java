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

import java.util.List;
import java.util.regex.Pattern;



/**
 * Generates features for different for the class of the token.
 * 
 * @deprecated Use {@link TokenClassFeatureGenerator} instead!
 */
@Deprecated 
public class FastTokenClassFeatureGenerator extends FeatureGeneratorAdapter {

  private static final String TOKEN_CLASS_PREFIX = "wc";
  private static final String TOKEN_AND_CLASS_PREFIX = "w&c";

  private static Pattern capPeriod;
  
  static {
    capPeriod = Pattern.compile("^[A-Z]\\.$");
  }
  
  private boolean generateWordAndClassFeature;

  
  
  public FastTokenClassFeatureGenerator() {
    this(false);
  }

  public FastTokenClassFeatureGenerator(boolean genearteWordAndClassFeature) {
    this.generateWordAndClassFeature = genearteWordAndClassFeature;
  }

  
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
    else if (pattern.isAllCapitalLetter() && token.length() == 1) {
      feat = "sc";
    }
    else if (pattern.isAllCapitalLetter()) {
      feat = "ac";
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
  
  
  public void createFeatures(List<String> features, String[] tokens, int index, String[] preds) {
    String wordClass = tokenFeature(tokens[index]);
    features.add(TOKEN_CLASS_PREFIX + "=" + wordClass);

    if (generateWordAndClassFeature) {
      features.add(TOKEN_AND_CLASS_PREFIX + "=" + tokens[index].toLowerCase()+","+wordClass);
    }
  }
}
