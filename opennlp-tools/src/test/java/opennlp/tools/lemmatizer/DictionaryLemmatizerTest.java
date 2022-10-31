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

package opennlp.tools.lemmatizer;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class DictionaryLemmatizerTest {

  private static DictionaryLemmatizer dictionaryLemmatizer;

  @BeforeClass
  public static void loadDictionary() throws Exception {
    dictionaryLemmatizer = new DictionaryLemmatizer(
        DictionaryLemmatizerTest.class.getResourceAsStream("/opennlp/tools/lemmatizer/smalldictionary.dict") 
    );
  }
  
  @Test
  public void testForNullPointerException() {
    String[] sentence = new String[]{"The","dogs","were","running","and","barking","down","the","street"};
    String[] sentencePOS = new String[]{"DT","NNS","VBD","VBG","CC","VBG","RP","DT","NN"};
    String[] expectedLemma = new String[]{"the","dog","is","run","and","bark","down","the","street"};
    
    String[] actualLemma = dictionaryLemmatizer.lemmatize(sentence, sentencePOS);
    
    for (int i = 0;i < sentence.length;i++) {
      // don't compare cases where the word is not in the dictionary...
      if (!actualLemma[i].equals("O")) Assert.assertEquals(expectedLemma[i], actualLemma[i]);
    }
  }

}
