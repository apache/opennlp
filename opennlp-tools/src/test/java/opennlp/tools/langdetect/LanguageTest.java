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

package opennlp.tools.langdetect;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LanguageTest {


  @Test
  void emptyConfidence() {
    String languageCode = "aLanguage";
    Language lang = new Language(languageCode);

    Assertions.assertEquals(languageCode, lang.getLang());
    Assertions.assertEquals(0, lang.getConfidence(), 0);
  }

  @Test
  void nonEmptyConfidence() {
    String languageCode = "aLanguage";
    double confidence = 0.05;
    Language lang = new Language(languageCode, confidence);

    Assertions.assertEquals(languageCode, lang.getLang());
    Assertions.assertEquals(confidence, lang.getConfidence(), 0);
  }

  @Test
  void emptyLanguage() {
    Assertions.assertThrows(NullPointerException.class, () -> new Language(null));
  }

  @Test
  void emptyLanguageConfidence() {
    Assertions.assertThrows(NullPointerException.class, () -> new Language(null, 0.05));
  }

  @Test
  void testToString() {
    Language lang = new Language("aLang");

    Assertions.assertEquals("aLang (0.0)", lang.toString());

    lang = new Language("aLang", 0.0886678);

    Assertions.assertEquals("aLang (0.0886678)", lang.toString());
  }


  @Test
  void testHash() {
    int hashA = new Language("aLang").hashCode();
    int hashAA = new Language("aLang").hashCode();
    int hashB = new Language("BLang").hashCode();
    int hashA5 = new Language("aLang", 5.0).hashCode();
    int hashA6 = new Language("BLang", 6.0).hashCode();

    Assertions.assertEquals(hashA, hashAA);

    Assertions.assertNotEquals(hashA, hashB);
    Assertions.assertNotEquals(hashA, hashA5);
    Assertions.assertNotEquals(hashB, hashA5);
    Assertions.assertNotEquals(hashA5, hashA6);
  }

  @Test
  void testEquals() {
    Language langA = new Language("langA");
    Language langB = new Language("langB");
    Language langA5 = new Language("langA5", 5.0);
    Language langA6 = new Language("langA5", 6.0);

    Assertions.assertEquals(langA, langA);
    Assertions.assertEquals(langA5, langA5);

    Assertions.assertNotEquals(langA, langA5);
    Assertions.assertNotEquals(langA, langB);

    Assertions.assertEquals(langA6, langA5);

    Assertions.assertNotEquals(langA.getLang(), "something else");
  }
}
