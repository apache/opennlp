package opennlp.tools.ml.model;

import java.util.HashMap;
import java.util.Map;

import opennlp.tools.ml.AbstractEventTrainer;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.ext.ExtensionLoader;

public class DataIndexerFactory {

  public static DataIndexer getDataIndexer(TrainingParameters parameters, Map<String, String> reportMap) {
    // The default is currently a 2-Pass data index.  Is this what we really want?
    String indexerParam = parameters.getStringParameter(AbstractEventTrainer.DATA_INDEXER_PARAM,
        AbstractEventTrainer.DATA_INDEXER_TWO_PASS_VALUE);

    // allow the user to pass in a report map.  If the don't, create one.
    if (reportMap == null) {
      reportMap = new HashMap<>();
    }

    DataIndexer indexer;
    switch (indexerParam) {
      case AbstractEventTrainer.DATA_INDEXER_ONE_PASS_VALUE:
        indexer = new OnePassDataIndexer();
        break;

      case AbstractEventTrainer.DATA_INDEXER_TWO_PASS_VALUE:
        indexer = new TwoPassDataIndexer();
        break;

      case AbstractEventTrainer.DATA_INDEXER_ONE_PASS_REAL_VALUE:
        indexer = new OnePassRealValueDataIndexer();
        break;

      default:
        // if the user passes in a class name for the indexer, try to instantiate the class.
        indexer = ExtensionLoader.instantiateExtension(DataIndexer.class, indexerParam);
    }


    indexer.init(parameters, reportMap);

    return indexer;
  }
}
