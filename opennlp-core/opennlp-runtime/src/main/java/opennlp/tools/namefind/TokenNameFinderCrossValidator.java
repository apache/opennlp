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

package opennlp.tools.namefind;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import opennlp.tools.commons.Sample;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.SequenceCodec;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.eval.CrossValidationPartitioner;
import opennlp.tools.util.eval.FMeasure;

/**
 * Cross validator for {@link TokenNameFinder}.
 */
public class TokenNameFinderCrossValidator {

  private record DocumentSample(NameSample[] samples) implements Sample {

    private static final long serialVersionUID = -4199121634493414749L;
  }

  /**
   * Reads {@link NameSample samples} to group them as a document based on the clear adaptive data flag.
   */
  private static class NameToDocumentSampleStream extends FilterObjectStream<NameSample, DocumentSample> {

    private NameSample beginSample;

    protected NameToDocumentSampleStream(ObjectStream<NameSample> samples) {
      super(samples);
    }

    @Override
    public DocumentSample read() throws IOException {

      List<NameSample> document = new ArrayList<>();

      if (beginSample == null) {
        // Assume that the clear flag is set
        beginSample = samples.read();
      }

      // Underlying stream is exhausted!
      if (beginSample == null) {
        return null;
      }

      document.add(beginSample);

      NameSample sample;
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

      return new DocumentSample(document.toArray(new NameSample[0]));
    }

    @Override
    public void reset() throws IOException, UnsupportedOperationException {
      super.reset();

      beginSample = null;
    }
  }

  /**
   * Splits {@link DocumentSample document samples} into {@link NameSample name samples}.
   */
  private static class DocumentToNameSampleStream extends FilterObjectStream<DocumentSample, NameSample> {

    protected DocumentToNameSampleStream(ObjectStream<DocumentSample> samples) {
      super(samples);
    }

    private Iterator<NameSample> documentSamples = Collections.emptyIterator();

    @Override
    public NameSample read() throws IOException {

      // Note: Empty document samples should be skipped

      if (documentSamples.hasNext()) {
        return documentSamples.next();
      }
      else {
        DocumentSample docSample = samples.read();

        if (docSample != null) {
          documentSamples = Arrays.asList(docSample.samples()).iterator();

          return read();
        }
        else {
          return null;
        }
      }
    }
  }

  private final String languageCode;
  private final TrainingParameters params;
  private final String type;
  private final byte[] featureGeneratorBytes;
  private final Map<String, Object> resources;
  private final TokenNameFinderEvaluationMonitor[] listeners;

  private final FMeasure fmeasure = new FMeasure();
  private TokenNameFinderFactory factory;
  
  /**
   * Initializes a {@link TokenNameFinderCrossValidator} with the given parameters.
   *
   * @param languageCode The ISO conform language code.
   * @param type {@code null} or an override type for all types in the training data.
   * @param featureGeneratorBytes The {@code byte[]} representing the feature generator descriptor.
   * @param resources Additional resources in a mapping.
   * @param codec The {@link SequenceCodec} to use.
   * @param params The {@link TrainingParameters} for the context of cross validation.
   * @param listeners the {@link TokenNameFinderEvaluationMonitor evaluation listeners}.
   */
  public TokenNameFinderCrossValidator(String languageCode, String type, TrainingParameters params,
                                       byte[] featureGeneratorBytes, Map<String, Object> resources,
                                       SequenceCodec<String> codec,
                                       TokenNameFinderEvaluationMonitor... listeners) {

    this.languageCode = languageCode;
    this.type = type;
    this.featureGeneratorBytes = featureGeneratorBytes;
    this.resources = resources;
    this.params = params;
    this.listeners = listeners;
  }

  /**
   * Initializes a {@link TokenNameFinderCrossValidator} with the given parameters.
   *
   * @param languageCode The ISO conform language code.
   * @param type {@code null} or an override type for all types in the training data.
   * @param featureGeneratorBytes The {@code byte[]} representing the feature generator descriptor.
   * @param resources Additional resources in a mapping.
   * @param listeners the {@link TokenNameFinderEvaluationMonitor evaluation listeners}.
   */
  public TokenNameFinderCrossValidator(String languageCode, String type, TrainingParameters trainParams,
                                       byte[] featureGeneratorBytes, Map<String, Object> resources,
                                       TokenNameFinderEvaluationMonitor... listeners) {
    this(languageCode, type, trainParams, featureGeneratorBytes, resources, new BioCodec(), listeners);
  }

  /**
   * Initializes a {@link TokenNameFinderCrossValidator} with the given parameters.
   *
   * @param languageCode The ISO conform language code.
   * @param type {@code null} or an override type for all types in the training data.
   * @param params The {@link TrainingParameters} for the context of cross validation.
   * @param factory The {@link TokenNameFinderFactory} for creating related objects.
   * @param listeners the {@link TokenNameFinderEvaluationMonitor evaluation listeners}.
   */
  public TokenNameFinderCrossValidator(String languageCode, String type,
      TrainingParameters params, TokenNameFinderFactory factory,
      TokenNameFinderEvaluationMonitor... listeners) {
    this(languageCode, type, params, null, null, new BioCodec(), listeners);
    this.factory = factory;
  }

  /**
   * Starts the evaluation.
   * <p>
   * Note:
   * The name samples need to be grouped on a document basis.
   *
   * @param samples The {@link ObjectStream} of {@link NameSample samples} to train and test with.
   * @param nFolds Number of folds. It must be greater than zero.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  public void evaluate(ObjectStream<NameSample> samples, int nFolds) throws IOException {

    CrossValidationPartitioner<DocumentSample> partitioner = new CrossValidationPartitioner<>(
        new NameToDocumentSampleStream(samples), nFolds);

    while (partitioner.hasNext()) {

      CrossValidationPartitioner.TrainingSampleStream<DocumentSample> trainingSampleStream =
          partitioner.next();

      TokenNameFinderModel model;
      if (factory != null) {
        model = NameFinderME.train(languageCode, type, new DocumentToNameSampleStream(trainingSampleStream),
            params, factory);
      }
      else {
        model = NameFinderME.train(languageCode, type, new DocumentToNameSampleStream(trainingSampleStream),
            params, TokenNameFinderFactory.create(null, featureGeneratorBytes, resources, new BioCodec()));
      }

      // do testing
      TokenNameFinderEvaluator evaluator = new TokenNameFinderEvaluator(
          new NameFinderME(model), listeners);

      evaluator.evaluate(new DocumentToNameSampleStream(trainingSampleStream.getTestSampleStream()));

      fmeasure.mergeInto(evaluator.getFMeasure());
    }
  }

  public FMeasure getFMeasure() {
    return fmeasure;
  }
}
