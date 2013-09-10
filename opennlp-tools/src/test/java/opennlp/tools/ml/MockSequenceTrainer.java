package opennlp.tools.ml;

import java.io.IOException;

import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.SequenceStream;

public class MockSequenceTrainer implements SequenceTrainer {

  public AbstractModel train(SequenceStream events) throws IOException {
    return null;
  }

}
