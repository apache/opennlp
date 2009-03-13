import opennlp.tools.util.featuregen.AdaptiveFeatureGenerator;
import opennlp.tools.util.featuregen.FeatureGeneratorFactory;
import opennlp.tools.util.featuregen.FeatureGeneratorResourceProvider;
import opennlp.tools.util.featuregen.TokenPatternFeatureGenerator;

public class GeneratorFactory implements FeatureGeneratorFactory {

  public AdaptiveFeatureGenerator createFeatureGenerator(
      FeatureGeneratorResourceProvider resourceProvider) {
    
    System.out.println("Hello from the modle gen factory :-)");
    
    return new TokenPatternFeatureGenerator();
  }
}
