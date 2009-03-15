package opennlp.model;

import java.util.Iterator;

/**
 * Class which turns a sequence stream into an event stream. 
 *
 */
public class SequenceStreamEventStream implements EventStream {

  private Iterator<Sequence> sequenceIterator;
  int eventIndex = -1;
  Event[] events;
  
  public SequenceStreamEventStream(SequenceStream sequenceStream) {
    this.sequenceIterator = sequenceStream.iterator();
  }
  
  public boolean hasNext() {
    if (events != null && eventIndex < events.length) {
      return true;
    }
    else {
      if (sequenceIterator.hasNext()) {
        Sequence s = sequenceIterator.next();
        eventIndex = 0;
        events = s.getEvents();
        return true;
      }
      else {
        return false;
      }
    }
  }

  public Event next() {
    return events[eventIndex++];
  }

  public void remove() {
    throw new UnsupportedOperationException();
  }

}
