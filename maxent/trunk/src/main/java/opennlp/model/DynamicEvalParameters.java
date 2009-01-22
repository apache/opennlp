package opennlp.model;

import java.util.List;

public class DynamicEvalParameters {
  
  /** Mapping between outcomes and paramater values for each context. 
   * The integer representation of the context can be found using <code>pmap</code>.*/
  private List<? extends Context> params;
  
  /** The number of outcomes being predicted. */
  private final int numOutcomes;
  
  
  /**
   * Creates a set of paramters which can be evaulated with the eval method.
   * @param params The parameters of the model.
   * @param numOutcomes The number of outcomes.
   */
  public DynamicEvalParameters(List<? extends Context> params, int numOutcomes) {
    this.params = params;
    this.numOutcomes = numOutcomes;
  }
  
  public Context[] getParams() {
    return params.toArray(new Context[params.size()]);
  }

  public int getNumOutcomes() {
    return numOutcomes;
  }

}
