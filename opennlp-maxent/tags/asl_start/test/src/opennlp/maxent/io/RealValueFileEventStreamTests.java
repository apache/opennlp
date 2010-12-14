package opennlp.maxent.io;

import java.io.IOException;

import junit.framework.TestCase;
import opennlp.model.OnePassRealValueDataIndexer;
import opennlp.model.RealValueFileEventStream;

public class RealValueFileEventStreamTests extends TestCase {

	public void testLastLineBug() throws IOException {
		RealValueFileEventStream rvfes = new RealValueFileEventStream("test/data/opennlp/maxent/io/rvfes-bug-data-ok.txt");
		OnePassRealValueDataIndexer indexer = new OnePassRealValueDataIndexer(rvfes, 1);
		assertEquals(1, indexer.getOutcomeLabels().length);
		
		rvfes = new RealValueFileEventStream("test/data/opennlp/maxent/io/rvfes-bug-data-broken.txt");
		indexer = new OnePassRealValueDataIndexer(rvfes, 1);
		assertEquals(1, indexer.getOutcomeLabels().length);
		
		
	}
}
