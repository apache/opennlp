package opennlp.tools.ml;

import static org.junit.Assert.*;
import opennlp.tools.ml.maxent.GIS;
import opennlp.tools.ml.perceptron.SimplePerceptronSequenceTrainer;
import opennlp.tools.util.TrainingParameters;

import org.junit.Before;
import org.junit.Test;

public class TrainerFactoryTest {
  
  private TrainingParameters mlParams;

  @Before
  public void setup() {
    mlParams = new TrainingParameters();
    mlParams.put(TrainingParameters.ALGORITHM_PARAM, GIS.MAXENT_VALUE);
    mlParams.put(TrainingParameters.ITERATIONS_PARAM, Integer.toString(10));
    mlParams.put(TrainingParameters.CUTOFF_PARAM, Integer.toString(5));
  }

  @Test
  public void testBuiltInValid() {
    assertTrue(TrainerFactory.isValid(mlParams.getSettings()));
  }

  @Test
  public void testSequenceTrainerValid() {
    mlParams.put(TrainingParameters.ALGORITHM_PARAM, MockSequenceTrainer.class.getCanonicalName());
    assertTrue(TrainerFactory.isValid(mlParams.getSettings()));
  }

  @Test
  public void testEventTrainerValid() {
    mlParams.put(TrainingParameters.ALGORITHM_PARAM, MockEventTrainer.class.getCanonicalName());
    assertTrue(TrainerFactory.isValid(mlParams.getSettings()));
  }

  @Test
  public void testInvalidTrainer() {
    mlParams.put(TrainingParameters.ALGORITHM_PARAM, "xyz");
    assertFalse(TrainerFactory.isValid(mlParams.getSettings()));
  }

  @Test
  public void testIsSequenceTrainerTrue() {
    mlParams.put(AbstractTrainer.ALGORITHM_PARAM,
        SimplePerceptronSequenceTrainer.PERCEPTRON_SEQUENCE_VALUE);

    assertTrue(TrainerFactory.isSequenceTraining(mlParams.getSettings()));
  }

  @Test
  public void testIsSequenceTrainerFalse() {
    mlParams.put(AbstractTrainer.ALGORITHM_PARAM,
        GIS.MAXENT_VALUE);

    assertFalse(TrainerFactory.isSequenceTraining(mlParams.getSettings()));
  }

}
