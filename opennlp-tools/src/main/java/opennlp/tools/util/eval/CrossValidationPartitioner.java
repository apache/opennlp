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


package opennlp.tools.util.eval;

import java.io.IOException;
import java.util.Collection;
import java.util.NoSuchElementException;

import opennlp.tools.util.CollectionObjectStream;
import opennlp.tools.util.ObjectStream;

/**
 * Provides access to training and test partitions for n-fold cross validation.
 * <p>
 * Cross validation is used to evaluate the performance of a classifier when only
 * training data is available. The training set is split into n parts
 * and the training / evaluation is performed n times on these parts.
 * The training partition always consists of n -1 parts and one part is used for testing.
 * <p>
 * To use the <code>CrossValidationPartioner</code> a client iterates over the n
 * <code>TrainingSampleStream</code>s. Each <code>TrainingSampleStream</code> represents
 * one partition and is used first for training and afterwards for testing.
 * The <code>TestSampleStream</code> can be obtained from the <code>TrainingSampleStream</code>
 * with the <code>getTestSampleStream</code> method.
 */
public class CrossValidationPartitioner<E> {

  /**
   * The <code>TestSampleStream</code> iterates over all test elements.
   *
   * @param <E>
   */
  private static class TestSampleStream<E> implements ObjectStream<E> {

    private ObjectStream<E> sampleStream;

    private final int numberOfPartitions;

    private final int testIndex;

    private int index;

    private boolean isPoisened;

    private TestSampleStream(ObjectStream<E> sampleStream, int numberOfPartitions, int testIndex) {
      this.numberOfPartitions = numberOfPartitions;
      this.sampleStream = sampleStream;
      this.testIndex = testIndex;
    }

    public E read() throws IOException {
      if (isPoisened) {
        throw new IllegalStateException();
      }

      // skip training samples
      while (index % numberOfPartitions != testIndex) {
        sampleStream.read();
        index++;
      }

      index++;

      return sampleStream.read();
    }

    /**
     * Throws <code>UnsupportedOperationException</code>
     */
    public void reset() {
      throw new UnsupportedOperationException();
    }

    public void close() throws IOException {
      sampleStream.close();
      isPoisened = true;
    }

    void poison() {
      isPoisened = true;
    }
  }

  /**
   * The <code>TrainingSampleStream</code> which iterates over
   * all training elements.
   *
   * Note:
   * After the <code>TestSampleStream</code> was obtained
   * the <code>TrainingSampleStream</code> must not be used
   * anymore, otherwise a {@link IllegalStateException}
   * is thrown.
   *
   * The <code>ObjectStream</code>s must not be used anymore after the
   * <code>CrossValidationPartitioner</code> was moved
   * to one of next partitions. If they are called anyway
   * a {@link IllegalStateException} is thrown.
   *
   * @param <E>
   */
  public static class TrainingSampleStream<E> implements ObjectStream<E> {

    private ObjectStream<E> sampleStream;

    private final int numberOfPartitions;

    private final int testIndex;

    private int index;

    private boolean isPoisened;

    private TestSampleStream<E> testSampleStream;

    TrainingSampleStream(ObjectStream<E> sampleStream, int numberOfPartitions, int testIndex) {
      this.numberOfPartitions = numberOfPartitions;
      this.sampleStream = sampleStream;
      this.testIndex = testIndex;
    }

    public E read() throws IOException {

      if (testSampleStream != null || isPoisened) {
        throw new IllegalStateException();
      }

      // If the test element is reached skip over it to not include it in
      // the training data
      if (index % numberOfPartitions == testIndex) {
        sampleStream.read();
        index++;
      }

      index++;

      return sampleStream.read();
    }

    /**
     * Resets the training sample. Use this if you need to collect things before
     * training, for example, to collect induced abbreviations or create a POS
     * Dictionary.
     *
     * @throws IOException
     */
    public void reset() throws IOException {
      if (testSampleStream != null || isPoisened) {
        throw new IllegalStateException();
      }
      this.index = 0;
      this.sampleStream.reset();
    }

    public void close() throws IOException {
      sampleStream.close();
      poison();
    }

    void poison() {
      isPoisened = true;
      if (testSampleStream != null)
        testSampleStream.poison();
    }

    /**
     * Retrieves the <code>ObjectStream</code> over the test/evaluations
     * elements and poisons this <code>TrainingSampleStream</code>.
     * From now on calls to the hasNext and next methods are forbidden
     * and will raise an<code>IllegalArgumentException</code>.
     *
     * @return the test sample stream
     */
    public ObjectStream<E> getTestSampleStream() throws IOException {

      if (isPoisened) {
        throw new IllegalStateException();
      }

      if (testSampleStream == null) {

        sampleStream.reset();
        testSampleStream =  new TestSampleStream<>(sampleStream, numberOfPartitions, testIndex);
      }

      return testSampleStream;
    }
  }

  /**
   * An <code>ObjectStream</code> over the whole set of data samples which
   * are used for the cross validation.
   */
  private ObjectStream<E> sampleStream;

  /**
   * The number of parts the data is divided into.
   */
  private final int numberOfPartitions;

  /**
   * The index of test part.
   */
  private int testIndex;

  /**
   * The last handed out <code>TrainingIterator</code>. The reference
   * is needed to poison the instance to fail fast if it is used
   * despite the fact that it is forbidden!.
   */
  private TrainingSampleStream<E> lastTrainingSampleStream;

  /**
   * Initializes the current instance.
   *
   * @param inElements
   * @param numberOfPartitions
   */
  public CrossValidationPartitioner(ObjectStream<E> inElements, int numberOfPartitions) {
    this.sampleStream = inElements;
    this.numberOfPartitions = numberOfPartitions;
  }

  /**
   * Initializes the current instance.
   *
   * @param elements
   * @param numberOfPartitions
   */
  public CrossValidationPartitioner(Collection<E> elements, int numberOfPartitions) {
    this(new CollectionObjectStream<>(elements), numberOfPartitions);
  }

  /**
   * Checks if there are more partitions available.
   */
  public boolean hasNext() {
    return testIndex < numberOfPartitions;
  }

  /**
   * Retrieves the next training and test partitions.
   */
  public TrainingSampleStream<E> next() throws IOException {
    if (hasNext()) {
      if (lastTrainingSampleStream != null)
        lastTrainingSampleStream.poison();

      sampleStream.reset();

      TrainingSampleStream<E> trainingSampleStream = new TrainingSampleStream<>(sampleStream,
          numberOfPartitions, testIndex);

      testIndex++;

      lastTrainingSampleStream = trainingSampleStream;

      return trainingSampleStream;
    }
    else {
      throw new NoSuchElementException();
    }
  }

  @Override
  public String toString() {
    return "At partition" + Integer.toString(testIndex + 1) +
        " of " + Integer.toString(numberOfPartitions);
  }
}
