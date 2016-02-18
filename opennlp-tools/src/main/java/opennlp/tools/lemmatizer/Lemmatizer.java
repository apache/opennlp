package opennlp.tools.lemmatizer;

/**
 * The interface for lemmatizers.
 */
public interface Lemmatizer {

  /**
   * Generates lemma tags for the word and postag returning the result in an array.
   *
   * @param toks an array of the tokens
   * @param tags an array of the pos tags
   *
   * @return an array of lemma classes for each token in the sequence.
   */
  public String[] lemmatize(String[] toks, String tags[]);

}
