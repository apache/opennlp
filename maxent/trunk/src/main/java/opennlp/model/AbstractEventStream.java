package opennlp.model;


public abstract class AbstractEventStream implements  EventStream {

  public AbstractEventStream() {
    super();
  }

  public void remove() {
    throw new UnsupportedOperationException();
  }

}
