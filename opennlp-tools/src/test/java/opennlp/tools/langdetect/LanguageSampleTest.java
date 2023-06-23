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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LanguageSampleTest {

  @Test
  void testConstructor() {
    Language lang = new Language("aLang");
    CharSequence context = "aContext";

    LanguageSample sample = new LanguageSample(lang, context);

    Assertions.assertEquals(lang, sample.language());
    Assertions.assertEquals(context, sample.context());
  }

  @Test
  void testLanguageSampleSerDe() throws IOException {
    Language lang = new Language("aLang");
    CharSequence context = "aContext";

    LanguageSample languageSample = new LanguageSample(lang, context);

    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    ObjectOutput out = new ObjectOutputStream(byteArrayOutputStream);
    out.writeObject(languageSample);
    out.flush();
    byte[] bytes = byteArrayOutputStream.toByteArray();

    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
    ObjectInput objectInput = new ObjectInputStream(byteArrayInputStream);

    LanguageSample deSerializedLanguageSample = null;
    try {
      deSerializedLanguageSample = (LanguageSample) objectInput.readObject();
    } catch (ClassNotFoundException e) {
      // do nothing
    }

    Assertions.assertNotNull(deSerializedLanguageSample);
    Assertions.assertEquals(languageSample.context(), deSerializedLanguageSample.context());
    Assertions.assertEquals(languageSample.language(), deSerializedLanguageSample.language());
    Assertions.assertEquals(languageSample, deSerializedLanguageSample);
  }

  @Test
  void testNullLang() {
    Assertions.assertThrows(NullPointerException.class, () -> {
      CharSequence context = "aContext";

      new LanguageSample(null, context);
    });

  }

  @Test
  void testNullContext() {
    Assertions.assertThrows(NullPointerException.class, () -> {
      Language lang = new Language("aLang");

      new LanguageSample(lang, null);
    });

  }

  @Test
  void testToString() {
    Language lang = new Language("aLang");
    CharSequence context = "aContext";

    LanguageSample sample = new LanguageSample(lang, context);

    Assertions.assertEquals(lang.getLang() + "\t" + context, sample.toString());
  }

  @Test
  void testHash() {

    int hashA = new LanguageSample(new Language("aLang"), "aContext").hashCode();
    int hashB = new LanguageSample(new Language("bLang"), "aContext").hashCode();
    int hashC = new LanguageSample(new Language("aLang"), "bContext").hashCode();

    Assertions.assertNotEquals(hashA, hashB);
    Assertions.assertNotEquals(hashA, hashC);
    Assertions.assertNotEquals(hashB, hashC);
  }

  @Test
  void testEquals() {

    LanguageSample sampleA = new LanguageSample(new Language("aLang"), "aContext");
    LanguageSample sampleA1 = new LanguageSample(new Language("aLang"), "aContext");
    LanguageSample sampleB = new LanguageSample(new Language("bLang"), "aContext");
    LanguageSample sampleC = new LanguageSample(new Language("aLang"), "bContext");

    Assertions.assertEquals(sampleA, sampleA);
    Assertions.assertEquals(sampleA, sampleA1);
    Assertions.assertNotEquals(sampleA, sampleB);
    Assertions.assertNotEquals(sampleA, sampleC);
    Assertions.assertNotEquals(sampleB, sampleC);
    Assertions.assertNotEquals(sampleA.context(), "something else");
  }
}
