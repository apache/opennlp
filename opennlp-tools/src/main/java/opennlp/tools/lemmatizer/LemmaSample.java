package opennlp.tools.lemmatizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Represents an lemmatized sentence.
 */
public class LemmaSample {

  private List<String> tokens;

  private List<String> tags;
  
  private final List<String> lemmas;

 /**
 * Represents one lemma sample.
 * @param tokens the token
 * @param tags the postags
 * @param lemmas the lemmas
 */
public LemmaSample(String[] tokens, String[] tags, String[] lemmas) {

    validateArguments(tokens.length, tags.length, lemmas.length);

    this.tokens = Collections.unmodifiableList(new ArrayList<String>(Arrays.asList(tokens)));
    this.tags = Collections.unmodifiableList(new ArrayList<String>(Arrays.asList(tags)));
    this.lemmas = Collections.unmodifiableList(new ArrayList<String>(Arrays.asList(lemmas)));
  }
  
  public LemmaSample(List<String> tokens, List<String> tags, List<String> lemmas) {

    validateArguments(tokens.size(), tags.size(), lemmas.size());

    this.tokens = Collections.unmodifiableList(new ArrayList<String>((tokens)));
    this.tags = Collections.unmodifiableList(new ArrayList<String>((tags)));
    this.lemmas = Collections.unmodifiableList(new ArrayList<String>((lemmas)));
  }

  public String[] getTokens() {
    return tokens.toArray(new String[tokens.size()]);
  }

  public String[] getTags() {
    return tags.toArray(new String[tags.size()]);
  }
  
  public String[] getLemmas() {
    return lemmas.toArray(new String[lemmas.size()]);
  }

  private void validateArguments(int tokensSize, int tagsSize, int lemmasSize) throws IllegalArgumentException {
    if (tokensSize != tagsSize || tagsSize != lemmasSize) {
      throw new IllegalArgumentException(
          "All arrays must have the same length: " +
              "sentenceSize: " + tokensSize +
              ", tagsSize: " + tagsSize +
              ", predsSize: " + lemmasSize + "!");
    }
  }

  @Override
  public String toString() {

        StringBuilder lemmaString = new StringBuilder();

        for (int ci = 0; ci < lemmas.size(); ci++) {
        lemmaString.append(tokens.get(ci)).append(" ").append(tags.get(ci)).append(" ").append(lemmas.get(ci)).append("\n");
        }
        return lemmaString.toString();
      }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    } else if (obj instanceof LemmaSample) {
      LemmaSample a = (LemmaSample) obj;
      return Arrays.equals(getTokens(), a.getTokens())
          && Arrays.equals(getTags(), a.getTags())
          && Arrays.equals(getLemmas(), a.getLemmas());
    } else {
      return false;
    }
  }
}
