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

import java.io.IOException;

import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.eval.CrossValidationPartitioner;
import opennlp.tools.util.eval.FMeasure;

public class ChunkerCrossValidator {

	private final String languageCode;
	private final int cutoff;
	private final int iterations;
	private FMeasure fmeasure = new FMeasure();

	public ChunkerCrossValidator(String languageCode, int cutoff, int iterations) {
		this.languageCode = languageCode;
		this.cutoff = cutoff;
		this.iterations = iterations;
	}

	public void evaluate(ObjectStream<ChunkSample> samples, int nFolds)
			throws IOException, InvalidFormatException, IOException {
		CrossValidationPartitioner<ChunkSample> partitioner = new CrossValidationPartitioner<ChunkSample>(
				samples, nFolds);

		while (partitioner.hasNext()) {

			CrossValidationPartitioner.TrainingSampleStream<ChunkSample> trainingSampleStream = partitioner
					.next();

			ChunkerModel model = ChunkerME.train(languageCode, trainingSampleStream,
					cutoff, iterations);

			// do testing
			ChunkerEvaluator evaluator = new ChunkerEvaluator(new ChunkerME(model));

			evaluator.evaluate(trainingSampleStream.getTestSampleStream());

			fmeasure.mergeInto(evaluator.getFMeasure());
		}
	}

	public FMeasure getFMeasure() {
		return fmeasure;
	}
}
