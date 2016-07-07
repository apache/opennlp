package opennlp.morfologik.tagdict;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import morfologik.stemming.Dictionary;
import opennlp.morfologik.builder.POSDictionayBuilderTest;
import opennlp.tools.postag.TagDictionary;

import org.junit.Test;

public class MorfologikTagDictionaryTest {

  @Test
  public void testNoLemma() throws Exception {
    MorfologikTagDictionary dict = createDictionary(false);

    List<String> tags = Arrays.asList(dict.getTags("carro"));
    assertEquals(1, tags.size());
    assertTrue(tags.contains("NOUN"));

  }

  @Test
  public void testPOSDictionaryInsensitive() throws Exception {
    TagDictionary dict = createDictionary(false);

    List<String> tags = Arrays.asList(dict.getTags("casa"));
    assertEquals(2, tags.size());
    assertTrue(tags.contains("NOUN"));
    assertTrue(tags.contains("V"));

    // this is the behavior of case insensitive dictionary
    // if we search it using case insensitive, Casa as a proper noun
    // should be lower case in the dictionary
    tags = Arrays.asList(dict.getTags("Casa"));
    assertEquals(2, tags.size());
    assertTrue(tags.contains("NOUN"));
    assertTrue(tags.contains("V"));

  }

  @Test
  public void testPOSDictionarySensitive() throws Exception {
    TagDictionary dict = createDictionary(true);

    List<String> tags = Arrays.asList(dict.getTags("casa"));
    assertEquals(2, tags.size());
    assertTrue(tags.contains("NOUN"));
    assertTrue(tags.contains("V"));

    // this is the behavior of case insensitive dictionary
    // if we search it using case insensitive, Casa as a proper noun
    // should be lower case in the dictionary
    tags = Arrays.asList(dict.getTags("Casa"));
    assertEquals(1, tags.size());
    assertTrue(tags.contains("PROP"));

  }

  private MorfologikTagDictionary createDictionary(boolean caseSensitive)
      throws Exception {
    return this.createDictionary(caseSensitive, null);
  }

  private MorfologikTagDictionary createDictionary(boolean caseSensitive,
      List<String> constant) throws Exception {

    Dictionary dic = Dictionary.read(POSDictionayBuilderTest.createMorfologikDictionary());
    MorfologikTagDictionary ml = new MorfologikTagDictionary(dic, caseSensitive);

    return ml;
  }

}
