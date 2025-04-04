package opennlp.tools.monitoring;

/** Used to identify the stop criteria for {@link opennlp.tools.ml.model.AbstractModel} training.*/
public interface StopCriteria {

    String TRAINING_FINISHED_DEFAULT_MSG = "Training Finished after completing %s Iterations successfully.";

    String getMessageIfSatisfied();

}