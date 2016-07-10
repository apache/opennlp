package opennlp.tools.cmdline.sentiment;

import java.io.OutputStream;

import opennlp.tools.cmdline.EvaluationErrorPrinter;
import opennlp.tools.sentiment.SentimentSample;
import opennlp.tools.util.eval.EvaluationMonitor;

public class SentimentEvaluationErrorListener
    extends EvaluationErrorPrinter<SentimentSample>
    implements EvaluationMonitor<SentimentSample> {

  public SentimentEvaluationErrorListener() {
    super(System.err);
  }

  protected SentimentEvaluationErrorListener(OutputStream outputStream) {
    super(outputStream);
  }

  @Override
  public void missclassified(SentimentSample reference,
      SentimentSample prediction) {
    printError(new String[] { reference.getSentiment() },
        new String[] { prediction.getSentiment() }, reference, prediction,
        reference.getSentence());
  }

}
