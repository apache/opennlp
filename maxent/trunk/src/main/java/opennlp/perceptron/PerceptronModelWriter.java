package opennlp.perceptron;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import opennlp.model.AbstractModel;
import opennlp.model.AbstractModelWriter;
import opennlp.model.ComparablePredicate;
import opennlp.model.Context;

/**
 * Abstract parent class for Perceptron writers.  It provides the persist method
 * which takes care of the structure of a stored document, and requires an
 * extending class to define precisely how the data should be stored.
 *
 * @author      Jason Baldridge
 * @version     $Revision: 1.1 $, $Date: 2009-01-22 23:23:34 $
 */
public abstract class PerceptronModelWriter extends AbstractModelWriter {
    protected Context[] PARAMS;
    protected String[] OUTCOME_LABELS;
    protected String[] PRED_LABELS;
    int numOutcomes;

    public PerceptronModelWriter (AbstractModel model) {
      
      Object[] data = model.getDataStructures();
      this.numOutcomes = model.getNumOutcomes();
      PARAMS = (Context[]) data[0];
      Map<String,Integer> pmap = (Map<String,Integer>)data[1];
      OUTCOME_LABELS = (String[])data[2];
      
      PRED_LABELS = new String[pmap.size()];
      for (String pred : pmap.keySet()) {
        PRED_LABELS[pmap.get(pred)] = pred;
      }
    }

    protected ComparablePredicate[] sortValues () {
      ComparablePredicate[] sortPreds;
      ComparablePredicate[] tmpPreds = new ComparablePredicate[PARAMS.length];
      int[] tmpOutcomes = new int[numOutcomes];
      double[] tmpParams = new double[numOutcomes];
      int numPreds = 0;
      //remove parameters with 0 weight and predicates with no parameters 
      for (int pid=0; pid<PARAMS.length; pid++) {
        int numParams = 0;    
        double[] predParams = PARAMS[pid].getParameters();
        for (int pi=0;pi<predParams.length;pi++) {
          if (predParams[pi] != 0d) {
            tmpOutcomes[numParams]=pi;
            tmpParams[numParams]=predParams[pi];
            numParams++;
          }
        }

        int[] activeOutcomes = new int[numParams];
        double[] activeParams = new double[numParams];

        for (int pi=0;pi<numParams;pi++) {
          activeOutcomes[pi] = tmpOutcomes[pi];
          activeParams[pi] = tmpParams[pi];
        }
        if (numParams != 0) {
          tmpPreds[numPreds] = new ComparablePredicate(PRED_LABELS[pid],activeOutcomes,activeParams);
          numPreds++;
        }
      }
      sortPreds = new ComparablePredicate[numPreds];
      for (int pid=0;pid<numPreds;pid++) {
        sortPreds[pid] = tmpPreds[pid];
      }
      Arrays.sort(sortPreds);
      return sortPreds;
    }
    
        
    protected List compressOutcomes (ComparablePredicate[] sorted) {
      ComparablePredicate cp = sorted[0];
      List outcomePatterns = new ArrayList();
      List newGroup = new ArrayList();
      for (int i=0; i<sorted.length; i++) {
        if (cp.compareTo(sorted[i]) == 0) {
          newGroup.add(sorted[i]);
        } else {	    
          cp = sorted[i];
          outcomePatterns.add(newGroup);
          newGroup = new ArrayList();
          newGroup.add(sorted[i]);
        }	    
      }
      outcomePatterns.add(newGroup);
      return outcomePatterns;
    }

    /**
     * Writes the model to disk, using the <code>writeX()</code> methods
     * provided by extending classes.
     *
     * <p>If you wish to create a PerceptronModelWriter which uses a different
     * structure, it will be necessary to override the persist method in
     * addition to implementing the <code>writeX()</code> methods.
     */
    public void persist() throws IOException {
      
      // the type of model (Perceptron)
      writeUTF("Perceptron");
      
      // the mapping from outcomes to their integer indexes
      writeInt(OUTCOME_LABELS.length);
      
      for (int i=0; i<OUTCOME_LABELS.length; i++)
        writeUTF(OUTCOME_LABELS[i]); 
      
      // the mapping from predicates to the outcomes they contributed to.
      // The sorting is done so that we actually can write this out more
      // compactly than as the entire list.
      ComparablePredicate[] sorted = sortValues();
      List compressed = compressOutcomes(sorted);
      
      writeInt(compressed.size());
      
      for (int i=0; i<compressed.size(); i++) {
        List a = (List)compressed.get(i);
        writeUTF(a.size()
            + ((ComparablePredicate)a.get(0)).toString());
      } 
      
      // the mapping from predicate names to their integer indexes
      writeInt(sorted.length);
      
      for (int i=0; i<sorted.length; i++)
        writeUTF(sorted[i].name); 
      
      // write out the parameters
      for (int i=0; i<sorted.length; i++)
        for (int j=0; j<sorted[i].params.length; j++)
          writeDouble(sorted[i].params[j]);
      
      close();
    }
}
