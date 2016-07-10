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

package opennlp.tools.sentiment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.eval.CrossValidationPartitioner;
import opennlp.tools.util.eval.FMeasure;

public class SentimentCrossValidator {

  private class DocumentSample {

    private SentimentSample samples[];

    DocumentSample(SentimentSample samples[]) {
      this.samples = samples;
    }

    private SentimentSample[] getSamples() {
      return samples;
    }
  }

  private class SentimentToDocumentSampleStream
      extends FilterObjectStream<SentimentSample, DocumentSample> {

    private SentimentSample beginSample;

    protected SentimentToDocumentSampleStream(
        ObjectStream<SentimentSample> samples) {
      super(samples);
    }

    public DocumentSample read() throws IOException {

      List<SentimentSample> document = new ArrayList<SentimentSample>();

      if (beginSample == null) {
        // Assume that the clear flag is set
        beginSample = samples.read();
      }

      // Underlying stream is exhausted!
      if (beginSample == null) {
        return null;
      }

      document.add(beginSample);

      SentimentSample sample;
      while ((sample = samples.read()) != null) {

        if (sample.isClearAdaptiveDataSet()) {
          beginSample = sample;
          break;
        }

        document.add(sample);
      }

      // Underlying stream is exhausted,
      // next call must return null
      if (sample == null) {
        beginSample = null;
      }

      return new DocumentSample(
          document.toArray(new SentimentSample[document.size()]));
    }

    @Override
    public void reset() throws IOException, UnsupportedOperationException {
      super.reset();

      beginSample = null;
    }
  }

  private class DocumentToSentimentSampleStream
      extends FilterObjectStream<DocumentSample, SentimentSample> {

    protected DocumentToSentimentSampleStream(
        ObjectStream<DocumentSample> samples) {
      super(samples);
    }

    private Iterator<SentimentSample> documentSamples = Collections
        .<SentimentSample> emptyList().iterator();

    public SentimentSample read() throws IOException {

      // Note: Empty document samples should be skipped

      if (documentSamples.hasNext()) {
        return documentSamples.next();
      } else {
        DocumentSample docSample = samples.read();

        if (docSample != null) {
          documentSamples = Arrays.asList(docSample.getSamples()).iterator();

          return read();
        } else {
          return null;
        }
      }
    }
  }

  private final String languageCode;
  private final TrainingParameters params;
  private SentimentEvaluationMonitor[] listeners;

  private SentimentFactory factory;
  private FMeasure fmeasure = new FMeasure();

  public SentimentCrossValidator(String lang, TrainingParameters params,
      SentimentFactory factory, SentimentEvaluationMonitor[] monitors) {

    this.languageCode = lang;
    this.factory = factory;
    this.params = params;
    this.listeners = monitors;
  }

  public void evaluate(ObjectStream<SentimentSample> samples, int nFolds)
      throws IOException {

    // Note: The sentiment samples need to be grouped on a document basis.

    CrossValidationPartitioner<DocumentSample> partitioner = new CrossValidationPartitioner<DocumentSample>(
        new SentimentToDocumentSampleStream(samples), nFolds);

    SentimentModel model = null;

    while (partitioner.hasNext()) {

      CrossValidationPartitioner.TrainingSampleStream<DocumentSample> trainingSampleStream = partitioner
          .next();

      if (factory != null) {
        model = SentimentME.train(languageCode,
            new DocumentToSentimentSampleStream(trainingSampleStream), params,
            factory);
      }

      // do testing
      SentimentEvaluator evaluator = new SentimentEvaluator(
          new SentimentME(model), listeners);

      evaluator.evaluate(new DocumentToSentimentSampleStream(
          trainingSampleStream.getTestSampleStream()));

      fmeasure.mergeInto(evaluator.getFMeasure());
    }
  }

  public FMeasure getFMeasure() {
    return fmeasure;
  }

}
