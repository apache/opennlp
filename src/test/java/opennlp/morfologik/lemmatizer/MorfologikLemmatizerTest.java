package opennlp.morfologik.lemmatizer;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.charset.Charset;

import morfologik.stemming.EncoderType;
import opennlp.morfologik.builder.MorfologikDictionayBuilder;
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

    MorfologikDictionayBuilder builder = new MorfologikDictionayBuilder();
    File dictInFile = new File(POSDictionayBuilderTest.class.getResource(
        "/dictionaryWithLemma.txt").getFile());

    File dictOutFile = File.createTempFile(
        POSDictionayBuilderTest.class.getName(), ".dict");

    builder.build(dictInFile, dictOutFile, Charset.forName("UTF-8"), "+", EncoderType.PREFIX);

    MorfologikLemmatizer ml = new MorfologikLemmatizer(dictOutFile.toURI()
        .toURL());

    return ml;
  }

}
