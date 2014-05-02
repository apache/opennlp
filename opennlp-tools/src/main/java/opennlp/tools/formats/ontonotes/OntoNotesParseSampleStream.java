package opennlp.tools.formats.ontonotes;

import java.io.IOException;

import opennlp.tools.parser.Parse;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;

// Should be possible with this one, to train the parser and pos tagger!
public class OntoNotesParseSampleStream extends FilterObjectStream<String, Parse> {

  protected OntoNotesParseSampleStream(ObjectStream<String> samples) {
    super(samples);
  }

  public Parse read() throws IOException {

    StringBuilder parseString = new StringBuilder();

    while(true) {
      String parse = samples.read();

      if (parse != null) {
        parse = parse.trim();
      }

      if (parse == null || parse.isEmpty()) {
        if (parseString.length() > 0) {
          return Parse.parseParse(parseString.toString());
        }
        else {
          return null;
        }
      }

      parseString.append(parse + " ");
    }
  }
}
