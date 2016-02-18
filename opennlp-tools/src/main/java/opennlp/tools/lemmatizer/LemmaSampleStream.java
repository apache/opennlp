package opennlp.tools.lemmatizer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.StringUtil;


/**
 * Reads data for training and testing. The format consists of:
 * word\tabpostag\tablemma.
 * @version 2016-02-16
 */
public class LemmaSampleStream extends FilterObjectStream<String, LemmaSample> {

  public LemmaSampleStream(ObjectStream<String> samples) {
    super(samples);
  }

  public LemmaSample read() throws IOException {

    List<String> toks = new ArrayList<String>();
    List<String> tags = new ArrayList<String>();
    List<String> preds = new ArrayList<String>();

    for (String line = samples.read(); line != null && !line.equals(""); line = samples.read()) {
      String[] parts = line.split("\t");
      if (parts.length != 3) {
        System.err.println("Skipping corrupt line: " + line);
      }
      else {
        toks.add(parts[0]);
        tags.add(parts[1]);
        String ses = StringUtil.getShortestEditScript(parts[0], parts[2]);
        preds.add(ses);
      }
    }
    if (toks.size() > 0) {
      LemmaSample lemmaSample = new LemmaSample(toks.toArray(new String[toks.size()]), tags.toArray(new String[tags.size()]), preds.toArray(new String[preds.size()]));
      return lemmaSample;
    }
    else {
      return null;
    }
  }
}
