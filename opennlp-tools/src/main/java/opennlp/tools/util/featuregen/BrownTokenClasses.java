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

import java.util.ArrayList;
import java.util.List;

/**
 * Obtain the paths listed in the pathLengths array from the Brown class.
 * This class is not to be instantiated.
 *
 */
public class BrownTokenClasses {

  public static final int[] pathLengths = { 4, 6, 10, 20 };

  /**
   * It provides a list containing the pathLengths for a token if found
   * in the Map:token,BrownClass.
   *
   * @param token the token to be looked up in the brown clustering map
   * @param brownLexicon the Brown clustering map
   * @return the list of the paths for a token
   */
  public static List<String> getWordClasses(String token, BrownCluster brownLexicon) {
    if (brownLexicon.lookupToken(token) == null) {
      return new ArrayList<>(0);
    } else {
      String brownClass = brownLexicon.lookupToken(token);
      List<String> pathLengthsList = new ArrayList<>();
      pathLengthsList.add(brownClass.substring(0, Math.min(brownClass.length(), pathLengths[0])));
      for (int i = 1; i < pathLengths.length; i++) {
        if (pathLengths[i - 1] < brownClass.length()) {
          pathLengthsList.add(brownClass.substring(0,
              Math.min(brownClass.length(), pathLengths[i])));
        }
      }
      return pathLengthsList;
    }
  }

}

