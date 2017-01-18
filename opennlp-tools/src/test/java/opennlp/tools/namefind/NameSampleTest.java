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

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.util.Span;

/**
 * This is the test class for {@link NameSample}.
 */
public class NameSampleTest {

  /**
   * Create a NameSample from scratch and validate it.
   *
   * @param useTypes if to use nametypes
   * @return the NameSample
   */
  private static NameSample createSimpleNameSample(boolean useTypes) {

    String[] sentence = {"U", ".", "S", ".", "President", "Barack", "Obama", "is",
        "considering", "sending", "additional", "American", "forces",
        "to", "Afghanistan", "."};

    Span[] names = {new Span(0, 4, "Location"), new Span(5, 7, "Person"),
        new Span(14, 15, "Location")};

    NameSample nameSample;
    if (useTypes) {
      nameSample = new NameSample(sentence, names, false);
    }
    else {
      Span[] namesWithoutType = new Span[names.length];
      for (int i = 0; i < names.length; i++) {
        namesWithoutType[i] = new Span(names[i].getStart(),
            names[i].getEnd());
      }

      nameSample = new NameSample(sentence, namesWithoutType, false);
    }

    return nameSample;
  }

  /**
   * Checks if could create a NameSample without NameTypes, generate the
   * string representation and validate it.
   */
  @Test
  public void testNoTypesToString() {
    String nameSampleStr = createSimpleNameSample(false).toString();

    Assert.assertEquals("<START> U . S . <END> President <START> Barack Obama <END>" +
        " is considering " +
        "sending additional American forces to <START> Afghanistan <END> .", nameSampleStr);
  }

  /**
   * Checks if could create a NameSample with NameTypes, generate the
   * string representation and validate it.
   */
  @Test
  public void testWithTypesToString() throws Exception {
    String nameSampleStr = createSimpleNameSample(true).toString();
    Assert.assertEquals("<START:Location> U . S . <END> President <START:Person>" +
            " Barack Obama <END> " +
        "is considering sending additional American forces to <START:Location> Afghanistan <END> .",
        nameSampleStr);

    NameSample parsedSample = NameSample.parse("<START:Location> U . S . <END> " +
        "President <START:Person> Barack Obama <END> is considering sending " +
        "additional American forces to <START:Location> Afghanistan <END> .",
        false);

    Assert.assertEquals(createSimpleNameSample(true), parsedSample);
  }

  /**
   * Checks that if the name is the last token in a sentence it is still outputed
   * correctly.
   */
  @Test
  public void testNameAtEnd() {

    String sentence[] = new String[] {
        "My",
        "name",
        "is",
        "Anna"
    };

    NameSample sample = new NameSample(sentence, new Span[]{new Span(3, 4)}, false);

    Assert.assertEquals("My name is <START> Anna <END>", sample.toString());
  }

  /**
   * Tests if an additional space is correctly treated as one space.
   *
   * @throws Exception
   */
  @Test
  public void testParseWithAdditionalSpace() throws Exception {
    String line = "<START> M . K . <END> <START> Schwitters <END> ?  <START> Heartfield <END> ?";

    NameSample test = NameSample.parse(line, false);

    Assert.assertEquals(8, test.getSentence().length);
  }

  /**
   * Checks if it accepts name type with some special characters
   */
  @Test
  public void testTypeWithSpecialChars() throws Exception {
    NameSample parsedSample = NameSample
        .parse(
            "<START:type-1> U . S . <END> "
                + "President <START:type_2> Barack Obama <END> is considering sending "
                + "additional American forces to <START:type_3-/;.,&%$> Afghanistan <END> .",
            false);

    Assert.assertEquals(3, parsedSample.getNames().length);
    Assert.assertEquals("type-1", parsedSample.getNames()[0].getType());
    Assert.assertEquals("type_2", parsedSample.getNames()[1].getType());
    Assert.assertEquals("type_3-/;.,&%$", parsedSample.getNames()[2].getType());
  }

  /**
   * Test if it fails to parse empty type
   */
  @Test(expected = IOException.class)
  public void testMissingType() throws Exception {
    NameSample.parse("<START:> token <END>", false);
  }

  /**
   * Test if it fails to parse type with space
   * @throws Exception
   */
  @Test(expected = IOException.class)
  public void testTypeWithSpace() throws Exception {
    NameSample.parse("<START:abc a> token <END>", false);
  }

  /**
   * Test if it fails to parse type with new line
   * @throws Exception
   */
  @Test(expected = IOException.class)
  public void testTypeWithNewLine() throws Exception {
    NameSample.parse("<START:abc\na> token <END>", false);
  }

  /**
   * Test if it fails to parse type with :
   * @throws Exception
   */
  @Test(expected = IOException.class)
  public void testTypeWithInvalidChar1() throws Exception {
    NameSample.parse("<START:abc:a> token <END>", false);
  }

  /**
   * Test if it fails to parse type with >
   * @throws Exception
   */
  @Test(expected = IOException.class)
  public void testTypeWithInvalidChar2() throws Exception {
    NameSample.parse("<START:abc>a> token <END>", false);
  }

  @Test
  public void testEquals() {
    Assert.assertFalse(createGoldSample() == createGoldSample());
    Assert.assertTrue(createGoldSample().equals(createGoldSample()));
    Assert.assertFalse(createGoldSample().equals(createPredSample()));
    Assert.assertFalse(createPredSample().equals(new Object()));
  }

  public static NameSample createGoldSample() {
    return createSimpleNameSample(true);
  }

  public static NameSample createPredSample() {
    return createSimpleNameSample(false);
  }
}
