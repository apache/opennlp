package opennlp.tools.ml.maxent;

import java.io.IOException;
import java.util.Map;

import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.Event;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;

public class MockDataIndexer implements DataIndexer {

  @Override
  public int[][] getContexts() {
    return new int[0][0];
  }

  @Override
  public int[] getNumTimesEventsSeen() {
    return new int[0];
  }

  @Override
  public int[] getOutcomeList() {
    return new int[0];
  }

  @Override
  public String[] getPredLabels() {
    return new String[0];
  }

  @Override
  public int[] getPredCounts() {
    return new int[0];
  }

  @Override
  public String[] getOutcomeLabels() {
    // TODO Auto-generated method stub
    return new String[0];
  }

  @Override
  public float[][] getValues() {
    return new float[0][0];
  }

  @Override
  public int getNumEvents() {
    return 0;
  }

  @Override
  public void init(TrainingParameters trainParams,
      Map<String, String> reportMap) {
  }

  @Override
  public void index(ObjectStream<Event> eventStream) throws IOException {
  }

}
