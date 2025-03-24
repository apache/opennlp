package opennlp.tools.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import static opennlp.tools.monitoring.StopCriteria.TRAINING_FINISHED_DEFAULT_MSG;

/**
 * Publishes ML model's Training progress to console.
 */
public class ConsoleTrainingProgressMonitor implements TrainingProgressMonitor {

  private static final Logger logger = LoggerFactory.getLogger(ConsoleTrainingProgressMonitor.class);

  /**
   * Keeps a track whether training was already finished because StopCriteria was met.
   */
  volatile boolean isTrainingFinished;

  private final List<String> progress = new LinkedList<>();

  @Override
  public void finishedIteration(int iteration, int numberCorrectEvents, int totalEvents) {
    double trainingAccuracy = (double) numberCorrectEvents / totalEvents;
    progress.add(String.format("%s: (%s/%s) %s", iteration, numberCorrectEvents, totalEvents, trainingAccuracy));
  }

  @Override
  public void finishedTraining(int iterations, int numberCorrectEvents, int totalEvents, StopCriteria stopCriteria) {
    if (!Objects.isNull(stopCriteria)) {
      progress.add(stopCriteria.getMessageIfSatisfied());
    } else {
      progress.add(String.format(TRAINING_FINISHED_DEFAULT_MSG, iterations));
    }
    isTrainingFinished = true;
  }

  @Override
  public void display() {
    progress.stream().forEach(logger::info);
  }

  @Override
  public boolean isTrainingFinished() {
    return isTrainingFinished;
  }
}