package opennlp.tools.lemmatizer;

import java.io.IOException;

import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.Sequence;
import opennlp.tools.ml.model.SequenceStream;
import opennlp.tools.util.ObjectStream;

public class LemmaSampleSequenceStream implements SequenceStream {

  private final ObjectStream<LemmaSample> samples;
  private final LemmatizerContextGenerator contextGenerator;

  public LemmaSampleSequenceStream(ObjectStream<LemmaSample> samples,
      LemmatizerContextGenerator contextGenerator) {
    this.samples = samples;
    this.contextGenerator = contextGenerator;
  }

  @Override
  public Sequence read() throws IOException {
    LemmaSample sample = samples.read();

    if (sample != null) {
      String sentence[] = sample.getTokens();
      String tags[] = sample.getTags();
      String preds[] = sample.getLemmas();
      Event[] events = new Event[sentence.length];

      for (int i=0; i < sentence.length; i++) {
        // it is safe to pass the tags as previous tags because
        // the context generator does not look for non predicted tags
        String[] context = contextGenerator.getContext(i, sentence, tags, preds);

        events[i] = new Event(tags[i], context);
      }
      return new Sequence<LemmaSample>(events,sample);
    }

    return null;
  }

  @Override
  public Event[] updateContext(Sequence sequence, AbstractModel model) {
    // TODO: Should be implemented for Perceptron sequence learning ...
    return null;
  }

  @Override
  public void reset() throws IOException, UnsupportedOperationException {
    samples.reset();
  }

  @Override
  public void close() throws IOException {
    samples.close();
  }
}
