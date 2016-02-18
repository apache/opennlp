package opennlp.tools.lemmatizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import opennlp.tools.ml.model.Event;
import opennlp.tools.util.AbstractEventStream;
import opennlp.tools.util.ObjectStream;

/**
 * Class for creating an event stream out of data files for training a probabilistic lemmatizer.
 */
public class LemmaSampleEventStream extends AbstractEventStream<LemmaSample> {

  private LemmatizerContextGenerator contextGenerator;

  /**
   * Creates a new event stream based on the specified data stream using the specified context generator.
   * @param d The data stream for this event stream.
   * @param cg The context generator which should be used in the creation of events for this event stream.
   */
  public LemmaSampleEventStream(ObjectStream<LemmaSample> d, LemmatizerContextGenerator cg) {
    super(d);
    this.contextGenerator = cg;
  }
  
  protected Iterator<Event> createEvents(LemmaSample sample) {

    if (sample != null) {
      List<Event> events = new ArrayList<Event>();
      String[] toksArray = sample.getTokens();
      String[] tagsArray = sample.getTags();
      String[] predsArray = sample.getLemmas();
      for (int ei = 0, el = sample.getTokens().length; ei < el; ei++) {
        events.add(new Event(predsArray[ei], contextGenerator.getContext(ei,toksArray,tagsArray,predsArray)));
      }
      return events.iterator();
    }
    else {
      return Collections.emptyListIterator();
    }
  }
}

