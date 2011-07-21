package opennlp.tools.dictionary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import opennlp.tools.util.InvalidFormatException;

import org.junit.Test;

public class CaseInsensitiveAbbreviationDictionaryTest {

  private AbbreviationDictionary getDict() {
    return new AbbreviationDictionary(false);
  }

  private AbbreviationDictionary getDict(InputStream in) throws IOException {
    return new AbbreviationDictionary(in, false);
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
    
    assertTrue(dict.contains(a.toUpperCase()));
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

    assertTrue(dictA.equals(dictB));
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

    // create a case insensitive dictionary
    AbbreviationDictionary dict = getDict();

    dict.add(entry1);

    // should return true because 1a = 1A in a case insensitive lookup
    assertTrue(dict.contains(entry2));
  }
}
