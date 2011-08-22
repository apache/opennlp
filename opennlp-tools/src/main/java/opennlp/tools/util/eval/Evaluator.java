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
import java.util.LinkedList;
import java.util.List;

import opennlp.tools.util.ObjectStream;

/**
 * The {@link Evaluator} is an abstract base class for evaluators.
 *
 * Evaluation results are the arithmetic mean of the
 * scores calculated for each reference sample.
 */
public abstract class Evaluator<T> {

  private List<EvaluationMonitor<T>> listeners;
  
  public Evaluator() {
    this.listeners = null;
  }
  
  public Evaluator(List<? extends EvaluationMonitor<T>> listeners) {
    if(listeners != null) {
      this.listeners = new LinkedList<EvaluationMonitor<T>>(listeners);
    }
  }
  
  /**
   * Evaluates the given reference sample object.
   * 
   * The implementation has to update the score after every invocation.
   *
   * @param reference the reference sample.
   * 
   * @return the predicted sample
   */
  protected T processSample(T reference) {
    // should be overridden by subclass... in the future we will make it abstract.
    return null;
  }

  /**
   * Evaluates the given reference object. The default implementation calls
   * {@link Evaluator#processSample(T)}
   * 
   * <p>
   * <b>note:</b> this method will be changed to private in the future.
   * Implementations should override {@link Evaluator#processSample(T)} instead.
   * If this method is override, the implementation has to update the score
   * after every invocation.
   * </p>
   * 
   * @param sample
   *          the sample to be evaluated
   */
  public void evaluateSample(T sample) {
    T predicted = processSample(sample);
    if(listeners != null) {
      if(sample.equals(predicted)) {
        for (EvaluationMonitor<T> listener : listeners) {
          listener.correctlyClassified(predicted, predicted);
        }
      } else {
        for (EvaluationMonitor<T> listener : listeners) {
          listener.missclassified(sample, predicted);
        } 
      }
    }
  }
  
  /**
   * Reads all sample objects from the stream
   * and evaluates each sample object with
   * {@link #evaluateSample(Object)} method.
   *
   * @param samples the stream of reference which
   * should be evaluated.
   * 
   */
  public void evaluate(ObjectStream<T> samples) throws IOException {
    T sample;
    while ((sample = samples.read()) != null) {
      evaluateSample(sample);
    }
  }
}
