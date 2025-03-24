package opennlp.tools.monitoring;

/** Monitors training progress of an {@link opennlp.tools.ml.model.AbstractModel} */

public interface TrainingProgressMonitor {

    void finishedIteration(int iteration, int numberCorrectEvents, int totalEvents);

    void finishedTraining(int iterations, int numberCorrectEvents, int totalEvents, StopCriteria stopCriteria);

    boolean isTrainingFinished();

    void display();
}