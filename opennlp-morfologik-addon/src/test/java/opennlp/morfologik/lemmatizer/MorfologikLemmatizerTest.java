package opennlp.morfologik.lemmatizer;

import static org.junit.Assert.assertEquals;

import java.nio.file.Path;

import opennlp.morfologik.builder.POSDictionayBuilderTest;
import opennlp.tools.lemmatizer.DictionaryLemmatizer;

import org.junit.Test;

public class MorfologikLemmatizerTest {

  @Test
  public void testLemmatizeInsensitive() throws Exception {
    DictionaryLemmatizer dict = createDictionary(false);

    assertEquals("casar", dict.lemmatize("casa", "V"));
    assertEquals("casa", dict.lemmatize("casa", "NOUN"));

    assertEquals("casa", dict.lemmatize("Casa", "PROP"));

  }

  private MorfologikLemmatizer createDictionary(boolean caseSensitive)
      throws Exception {

    Path output = POSDictionayBuilderTest.createMorfologikDictionary();

    MorfologikLemmatizer ml = new MorfologikLemmatizer(output);

    return ml;
  }

}
