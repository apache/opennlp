package opennlp.morfologik.lemmatizer;

import static org.junit.Assert.*;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import opennlp.morfologik.builder.POSDictionayBuilderTest;
import opennlp.tools.lemmatizer.Lemmatizer;

public class MorfologikLemmatizerTest {

  @Test
  public void testLemmatizeInsensitive() throws Exception {
    Lemmatizer dict = createDictionary(false);
    
    
    String[] toks = {"casa", "casa", "Casa"};
    String[] tags = {"V", "NOUN", "PROP"};
    
    String[] lemmas = dict.lemmatize(toks, tags);

    assertEquals("casar", lemmas[0]);
    assertEquals("casa", lemmas[1]);

    // lookup is case insensitive. There is no entry casa - prop
    assertNull(lemmas[2]);

  }
  
  @Test
  public void testLemmatizeMultiLemma() throws Exception {
    MorfologikLemmatizer dict = createDictionary(false);
    
    
    String[] toks = {"foi"};
    String[] tags = {"V"};
    
    List<List<String>> lemmas = dict.lemmatize(Arrays.asList(toks), Arrays.asList(tags));

    
    assertTrue(lemmas.get(0).contains("ir"));
    assertTrue(lemmas.get(0).contains("ser"));
    

  }

  private MorfologikLemmatizer createDictionary(boolean caseSensitive)
      throws Exception {

    Path output = POSDictionayBuilderTest.createMorfologikDictionary();

    MorfologikLemmatizer ml = new MorfologikLemmatizer(output);

    return ml;
  }

}
