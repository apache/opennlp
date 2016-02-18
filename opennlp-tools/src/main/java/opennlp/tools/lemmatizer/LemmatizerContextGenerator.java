package opennlp.tools.lemmatizer;

import opennlp.tools.util.BeamSearchContextGenerator;

/**
 * Interface for the context generator used for probabilistic lemmatizer.
 */
public interface LemmatizerContextGenerator extends BeamSearchContextGenerator<String> {

  /**
   * Returns the contexts for lemmatizing of the specified index.
   * @param i The index of the token in the specified toks array for which the context should be constructed.
   * @param toks The tokens of the sentence.  The <code>toString</code> methods of these objects should return the token text.
   * @param tags The POS tags for the the specified tokens.
   * @param preds The previous decisions made in the tagging of this sequence.  Only indices less than i will be examined.
   * @return An array of predictive contexts on which a model basis its decisions.
   */
  public String[] getContext(int i, String[] toks, String[] tags, String[] preds);
}

