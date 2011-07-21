package opennlp.tools.dictionary;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;

import opennlp.tools.util.InvalidFormatException;

import org.junit.Test;

public class CaseSensitiveAbbreviationDictionaryTest {

  private AbbreviationDictionary getDict() {
    return new AbbreviationDictionary(true);
  }

  private AbbreviationDictionary getDict(InputStream in) throws IOException {
    return new AbbreviationDictionary(in, true);
  }

  /**
   * Tests a basic lookup.
   */
  @Test
  public void testLookup() {

    String a = "a";
    String b = "b";

    AbbreviationDictionary dict = getDict();

    dict.add(a);

    assertTrue(dict.contains(a));
    assertFalse(dict.contains(b));
    
    assertFalse(dict.contains(a.toUpperCase()));
  }

  /**
   * Tests set.
   */
  @Test
  public void testSet() {

    String a = "a";
    String a1 = "a";

    AbbreviationDictionary dict = getDict();

    dict.add(a);
    dict.add(a1);

    assertTrue(dict.contains(a));
    assertEquals(1, dict.size());
  }
  
  /**
   * Tests set.
   */
  @Test
  public void testSetDiffCase() {

    String a = "a";
    String a1 = "A";

    AbbreviationDictionary dict = getDict();

    dict.add(a);
    dict.add(a1);

    assertTrue(dict.contains(a));
    assertEquals(2, dict.size());
  }

  /**
   * Tests serialization and deserailization of the {@link Dictionary}.
   * 
   * @throws IOException
   * @throws InvalidFormatException
   */
  @Test
  public void testSerialization() throws IOException, InvalidFormatException {
    AbbreviationDictionary reference = getDict();

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

    AbbreviationDictionary recreated = getDict(new ByteArrayInputStream(
        out.toByteArray()));

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
    // this test is independent of the case sensitive flag.
    
    String testDictionary = "1a \n 1b \n 1c\n 1d";

    AbbreviationDictionary dictionay = AbbreviationDictionary
        .parseOneEntryPerLine(new StringReader(testDictionary));

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

    AbbreviationDictionary dictA = getDict();
    dictA.add(entry1);
    dictA.add(entry2);

    AbbreviationDictionary dictB = getDict();
    dictB.add(entry1);
    dictB.add(entry2);

    assertTrue(dictA.equals(dictB));
  }
  
  /**
   * Tests for the {@link Dictionary#equals(Object)} method.
   */
  @Test
  public void testEqualsDifferentCase() {

    AbbreviationDictionary dictA = getDict();
    dictA.add("1a");
    dictA.add("1b");

    AbbreviationDictionary dictB = getDict();
    dictB.add("1A");
    dictB.add("1B");

    // should fail in case sensitive dict
    assertFalse(dictA.equals(dictB));
  }

  /**
   * Tests the {@link Dictionary#hashCode()} method.
   */
  @Test
  public void testHashCode() {
    String entry1 = "a1";

    AbbreviationDictionary dictA = getDict();
    dictA.add(entry1);

    AbbreviationDictionary dictB = getDict();
    dictB.add(entry1);

    assertEquals(dictA.hashCode(), dictB.hashCode());
  }
  
  /**
   * Tests the {@link Dictionary#hashCode()} method.
   */
  @Test
  public void testHashCodeDifferentCase() {
    String entry1 = "a1";

    AbbreviationDictionary dictA = getDict();
    dictA.add(entry1);

    AbbreviationDictionary dictB = getDict();
    dictB.add(entry1.toUpperCase());

    // TODO: should it be equal??
    assertNotSame(dictA.hashCode(), dictB.hashCode());
  }

  /**
   * Tests the lookup of tokens of different case.
   */
  @Test
  public void testDifferentCaseLookup() {

    String entry1 = "1a";
    String entry2 = "1A";

    // create a case sensitive dictionary
    AbbreviationDictionary dict = getDict();

    dict.add(entry1);

    // should return false because 1a != 1A in a case sensitive lookup
    assertFalse(dict.contains(entry2));
  }
}
