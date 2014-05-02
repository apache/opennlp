package opennlp.tools.formats.ontonotes;

import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.formats.AbstractSampleStreamFactory;
import opennlp.tools.formats.convert.ParseToPOSSampleStream;
import opennlp.tools.parser.Parse;
import opennlp.tools.postag.POSSample;
import opennlp.tools.util.ObjectStream;

public class OntoNotesPOSSampleStreamFactory extends AbstractSampleStreamFactory<POSSample> {

  private OntoNotesParseSampleStreamFactory parseSampleStreamFactory =
      new OntoNotesParseSampleStreamFactory();

  protected OntoNotesPOSSampleStreamFactory() {
    super(OntoNotesFormatParameters.class);
  }

  public ObjectStream<POSSample> create(String[] args) {
    ObjectStream<Parse> parseSampleStream = parseSampleStreamFactory.create(args);
    return new ParseToPOSSampleStream(parseSampleStream);
  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(POSSample.class, "ontonotes",
        new OntoNotesPOSSampleStreamFactory());
  }
}
