/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.chunker;

import static junit.framework.Assert.assertNotSame;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import opennlp.tools.cmdline.chunker.ChunkEvaluationErrorListener;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.eval.FMeasure;

import org.junit.Test;

/**
 * Tests for {@link ChunkerEvaluator}.
 * 
 * @see ChunkerEvaluator
 */
public class ChunkerEvaluatorTest {
	
	private static final double DELTA = 1.0E-9d;

	/**
	 * Checks the evaluator results against the results got using the conlleval,
	 * available at http://www.cnts.ua.ac.be/conll2000/chunking/output.html
	 * The output.txt file has only 3 sentences, but can be replaced by the one
	 * available at the conll2000 site to validate using a bigger sample.
	 * @throws IOException
	 */
	@Test
	public void testEvaluator() throws IOException {
		InputStream inPredicted = getClass().getClassLoader().getResourceAsStream(
				"opennlp/tools/chunker/output.txt");
		InputStream inExpected = getClass().getClassLoader().getResourceAsStream(
		"opennlp/tools/chunker/output.txt");

		String encoding = "UTF-8";

		DummyChunkSampleStream predictedSample = new DummyChunkSampleStream(
				new PlainTextByLineStream(new InputStreamReader(inPredicted, encoding)), true);
		
		DummyChunkSampleStream expectedSample = new DummyChunkSampleStream(
				new PlainTextByLineStream(new InputStreamReader(inExpected)), false);
		
		Chunker dummyChunker = new DummyChunker(predictedSample);
		
		OutputStream stream = new ByteArrayOutputStream();
		ChunkerEvaluationMonitor listener = new ChunkEvaluationErrorListener(stream);
		ChunkerEvaluator evaluator = new ChunkerEvaluator(dummyChunker, listener);
		
		evaluator.evaluate(expectedSample);
		
		FMeasure fm = evaluator.getFMeasure();
		
		assertEquals(0.8d, fm.getPrecisionScore(), DELTA);
		assertEquals(0.875d, fm.getRecallScore(), DELTA);
		
		assertNotSame(stream.toString().length(), 0);
		
	}
	
  @Test
  public void testEvaluatorNoError() throws IOException {
    InputStream inPredicted = getClass().getClassLoader().getResourceAsStream(
        "opennlp/tools/chunker/output.txt");
    InputStream inExpected = getClass().getClassLoader().getResourceAsStream(
        "opennlp/tools/chunker/output.txt");

    String encoding = "UTF-8";

    DummyChunkSampleStream predictedSample = new DummyChunkSampleStream(
        new PlainTextByLineStream(new InputStreamReader(inPredicted, encoding)),
        true);

    DummyChunkSampleStream expectedSample = new DummyChunkSampleStream(
        new PlainTextByLineStream(new InputStreamReader(inExpected, encoding)),
        true);

    Chunker dummyChunker = new DummyChunker(predictedSample);

    OutputStream stream = new ByteArrayOutputStream();
    ChunkerEvaluationMonitor listener = new ChunkEvaluationErrorListener(
        stream);
    ChunkerEvaluator evaluator = new ChunkerEvaluator(dummyChunker, listener);

    evaluator.evaluate(expectedSample);

    FMeasure fm = evaluator.getFMeasure();

    assertEquals(1d, fm.getPrecisionScore(), DELTA);
    assertEquals(1d, fm.getRecallScore(), DELTA);

    assertEquals(stream.toString().length(), 0);

  }

}
