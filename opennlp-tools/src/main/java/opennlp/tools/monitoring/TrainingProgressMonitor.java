package opennlp.tools.monitoring;

public interface TrainingProgressMonitor {

    void finishedIteration(int iteration, int numberCorrectEvents, int totalEvents);

    void finishedTraining(int iterations, int numberCorrectEvents, int totalEvents, StopCriteria stopCriteria);

    boolean isTrainingFinished();

    void display();
}