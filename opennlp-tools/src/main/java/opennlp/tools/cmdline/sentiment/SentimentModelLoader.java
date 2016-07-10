package opennlp.tools.cmdline.sentiment;

import java.io.IOException;
import java.io.InputStream;

import opennlp.tools.cmdline.ModelLoader;
import opennlp.tools.sentiment.SentimentModel;
import opennlp.tools.util.InvalidFormatException;

public class SentimentModelLoader extends ModelLoader<SentimentModel> {

  public SentimentModelLoader() {
    super("Sentiment");
  }

  @Override
  protected SentimentModel loadModel(InputStream modelIn)
      throws IOException, InvalidFormatException {
    return new SentimentModel(modelIn);
  }
}
