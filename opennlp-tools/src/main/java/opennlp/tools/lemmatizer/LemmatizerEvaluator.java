package opennlp.tools.lemmatizer;

import opennlp.tools.util.eval.Evaluator;
import opennlp.tools.util.eval.Mean;

/**
 * The {@link LemmatizerEvaluator} measures the performance of
 * the given {@link Lemmatizer} with the provided reference
 * {@link LemmaSample}s.
 */
public class LemmatizerEvaluator extends Evaluator<LemmaSample> {

  private Lemmatizer lemmatizer;

  private Mean wordAccuracy = new Mean();

  /**
   * Initializes the current instance.
   *
   * @param aLemmatizer a lemmatizer
   * @param listeners an array of evaluation listeners
   */
  public LemmatizerEvaluator(Lemmatizer aLemmatizer, LemmatizerEvaluationMonitor ... listeners) {
    super(listeners);
    this.lemmatizer = aLemmatizer;
  }

  /**
   * Evaluates the given reference {@link LemmaSample} object.
   *
   * This is done by tagging the sentence from the reference
   * {@link LemmaSample} with the {@link Lemmatizer}. The
   * tags are then used to update the word accuracy score.
   *
   * @param reference the reference {@link LemmaSample}.
   *
   * @return the predicted {@link LemmaSample}.
   */
  @Override
  protected LemmaSample processSample(LemmaSample reference) {

    String[] predictedLemmas = lemmatizer.lemmatize(reference.getTokens(), reference.getTags());
    String[] referenceLemmas = reference.getLemmas();
    
    for (int i = 0; i < referenceLemmas.length; i++) {
      //System.err.println("-> Reference: " + referenceLemmas[i]);
      //System.err.println("-> Predicted: " + predictedLemmas[i]);
      if (referenceLemmas[i].equals(predictedLemmas[i])) {
        wordAccuracy.add(1);
      }
      else {
        wordAccuracy.add(0);
      }
    }
    return new LemmaSample(reference.getTokens(), reference.getTags(), predictedLemmas);
  }

  /**
   * Retrieves the word accuracy.
   *
   * This is defined as:
   * word accuracy = correctly detected tags / total words
   *
   * @return the word accuracy
   */
  public double getWordAccuracy() {
    return wordAccuracy.mean();
  }

  /**
   * Retrieves the total number of words considered
   * in the evaluation.
   *
   * @return the word count
   */
  public long getWordCount() {
    return wordAccuracy.count();
  }

  /**
   * Represents this objects as human readable {@link String}.
   */
  @Override
  public String toString() {
    return "Accuracy:" + wordAccuracy.mean() +
        " Number of Samples: " + wordAccuracy.count();
  }
}
