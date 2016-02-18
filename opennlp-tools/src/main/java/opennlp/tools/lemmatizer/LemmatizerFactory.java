package opennlp.tools.lemmatizer;

import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.SequenceValidator;
import opennlp.tools.util.ext.ExtensionLoader;

public class LemmatizerFactory extends BaseToolFactory {

  /**
   * Creates a {@link LemmatizerFactory} that provides the default implementation
   * of the resources.
   */
  public LemmatizerFactory() {
  }

  public static LemmatizerFactory create(String subclassName)
      throws InvalidFormatException {
    if (subclassName == null) {
      // will create the default factory
      return new LemmatizerFactory();
    }
    try {
      LemmatizerFactory theFactory = ExtensionLoader.instantiateExtension(
          LemmatizerFactory.class, subclassName);
      return theFactory;
    } catch (Exception e) {
      String msg = "Could not instantiate the " + subclassName
          + ". The initialization throw an exception.";
      System.err.println(msg);
      e.printStackTrace();
      throw new InvalidFormatException(msg, e);
    }
  }

  @Override
  public void validateArtifactMap() throws InvalidFormatException {
    // no additional artifacts
  }

  public SequenceValidator<String> getSequenceValidator() {
    return new DefaultLemmatizerSequenceValidator();
  }

  public LemmatizerContextGenerator getContextGenerator() {
    return new DefaultLemmatizerContextGenerator();
  }
}
