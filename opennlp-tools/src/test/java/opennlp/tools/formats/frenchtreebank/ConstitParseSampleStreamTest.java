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

package opennlp.tools.formats.frenchtreebank;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.parser.Parse;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;

public class ConstitParseSampleStreamTest {

  private String sample1Tokens[] = new String[]{
      "L'",
      "autonomie",
      "de",
      "la",
      "Bundesbank",
      ",",
      "la",
      "politique",
      "de",
      "stabilité",
      "qu'",
      "elle",
      "a",
      "fait",
      "prévaloir",
      "(",
      "avec",
      "moins",
      "de",
      "succès",
      "et",
      "de",
      "sévérité",
      "qu'",
      "on",
      "ne",
      "le",
      "dit",
      ",",
      "mais",
      "tout",
      "est",
      "relatif",
      ")",
      ",",
      "est",
      "une",
      "pièce",
      "essentielle",
      "de",
      "la",
      "division",
      "des",
      "pouvoirs",
      "en",
      "Allemagne",
      "."
  };

  /**
   * Reads sample1.xml into a byte array.
   *
   * @return byte array containing sample1.xml.
   */
  private static byte[] getSample1() throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    byte buffer[] = new byte[1024];
    int length;
    try (InputStream sampleIn =
        ConstitParseSampleStreamTest.class.getResourceAsStream("sample1.xml")) {
      while ((length = sampleIn.read(buffer)) > 0) {
        out.write(buffer, 0, length);
      }
    }

    return out.toByteArray();
  }

  @Test
  public void testThereIsExactlyOneSent() throws IOException {
    ObjectStream<Parse> samples =
        new ConstitParseSampleStream(ObjectStreamUtils.createObjectStream(getSample1()));

    Assert.assertNotNull(samples.read());
    Assert.assertNull(samples.read());
    Assert.assertNull(samples.read());
  }

  @Test
  public void testTokensAreCorrect() throws IOException {

    ObjectStream<Parse> samples =
        new ConstitParseSampleStream(ObjectStreamUtils.createObjectStream(getSample1()));

    Parse p = samples.read();

    Parse[] tagNodes = p.getTagNodes();
    String[] tokens = new String[tagNodes.length];
    for (int ti = 0; ti < tagNodes.length; ti++) {
      tokens[ti] = tagNodes[ti].getCoveredText();
    }

    Assert.assertArrayEquals(sample1Tokens, tokens);
  }
}
