package opennlp.tools.dictionary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;

import opennlp.tools.util.InvalidFormatException;

import org.junit.Test;

public class AbbreviationDictionaryTest {

  /**
   * Tests a basic lookup.
   */
  @Test
  public void testLookup() {

    String a = "a";
    String b = "b";

    AbbreviationDictionary dict = new AbbreviationDictionary();

    dict.add(a);

    assertTrue(dict.contains(a));
    assertTrue(!dict.contains(b));
  }
  
  /**
   * Tests a basic lookup.
   */
  @Test
  public void testSet() {

    String a = "a";
    String a1 = "a";

    AbbreviationDictionary dict = new AbbreviationDictionary();

    dict.add(a);
    dict.add(a1);

    assertTrue(dict.contains(a));
    assertEquals(1, dict.size());
  }
  
  /**
   * Tests serialization and deserailization of the {@link Dictionary}.
   *
   * @throws IOException
   * @throws InvalidFormatException
   */
  @Test
  public void testSerialization() throws IOException, InvalidFormatException {
    AbbreviationDictionary reference = new AbbreviationDictionary();

    String a1 = "a1";
    String a2 = "a2";
    String a3 = "a3";
    String a5 = "a5";

    reference.add(a1);
    reference.add(a2);
    reference.add(a3);
    reference.add(a5);

    ByteArrayOutputStream out = new ByteArrayOutputStream();

    reference.serialize(out);

    AbbreviationDictionary recreated = new AbbreviationDictionary(
        new ByteArrayInputStream(out.toByteArray()));

    assertTrue(reference.equals(recreated));
  }
  
  /**
   * Tests for the {@link Dictionary#parseOneEntryPerLine(java.io.Reader)}
   * method.
   *
   * @throws IOException
   */
  @Test
  public void testParseOneEntryPerLine() throws IOException {

    String testDictionary = "1a \n 1b \n 1c\n 1d";

    AbbreviationDictionary dictionay =
      AbbreviationDictionary.parseOneEntryPerLine(new StringReader(testDictionary));

    assertTrue(dictionay.size() == 4);

    assertTrue(dictionay.contains("1a"));
    assertTrue(dictionay.contains("1b"));
    assertTrue(dictionay.contains("1c"));
    assertTrue(dictionay.contains("1d"));
  }
  
  /**
   * Tests for the {@link Dictionary#equals(Object)} method.
   */
  @Test
  public void testEquals() {
    String entry1 = "1a";
    String entry2 = "1b";

    AbbreviationDictionary dictA = new AbbreviationDictionary();
    dictA.add(entry1);
    dictA.add(entry2);

    AbbreviationDictionary dictB = new AbbreviationDictionary();
    dictB.add(entry1);
    dictB.add(entry2);

    assertTrue(dictA.equals(dictB));
  }
  
  /**
   * Tests the {@link Dictionary#hashCode()} method.
   */
  @Test
  public void testHashCode() {
    String entry1 = "a1";

    AbbreviationDictionary dictA = new AbbreviationDictionary();
    dictA.add(entry1);

    AbbreviationDictionary dictB = new AbbreviationDictionary();
    dictB.add(entry1);

    assertEquals(dictA.hashCode(), dictB.hashCode());
  }
  
  /**
   * Tests the lookup of tokens of different case.
   */
  @Test
  public void testDifferentCaseLookup() {

    String entry1 = "1a";
    String entry2 = "1A";

    AbbreviationDictionary dict = new AbbreviationDictionary(false);

    dict.add(entry1);

    assertTrue(dict.contains(entry2));
  }
  
  /**
   * Tests the lookup of tokens of different case.
   */
  @Test
  public void testDifferentCaseLookupCaseSensitive() {

    String entry1 = "1a";
    String entry2 = "1A";

    AbbreviationDictionary dict = new AbbreviationDictionary(true);

    dict.add(entry1);

    assertFalse(dict.contains(entry2));
  }
}
