/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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


package opennlp.tools.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Provides access to training and test partitions for n-fold cross validation.
 * 
 * Cross validation is used to evaluate the performance of a classifier when only
 * training data is available. The training set is split into n parts
 * and the training / evaluation is performed n times on these parts. 
 * The training partition always consists of n -1 parts and one part is used for testing.
 * 
 * To use the <code>CrossValidationPartioner</code> a client iterates over the n
 * {@link TrainingIterator}s. Each </code>TrainingIterator</code> represents
 * one partition and is used first for training and afterwards for testing.
 * The test <code>Iterator</code> can be obtained from the <code>TrainingIterator</code>
 * with the <code>getTestIterator</code> method.
 */
public class CrossValidationPartitioner<E> implements Iterator<CrossValidationPartitioner.TrainingIterator<E>>, Iterable<CrossValidationPartitioner.TrainingIterator<E>> {

  /**
   * The <code>TestIterator</code> iterates which iterates over all test elements.
   *
   * @param <E>
   */
  private static class TestIterator<E> implements Iterator<E> {
    
    private Iterator<E> dataIterator;
    
    private final int numberOfPartitions;
    
    private final int testIndex;
    
    private int index;
    
    private boolean isPoisened;
    
    private TestIterator(Iterator<E> dataIterator, int numberOfPartitions, int testIndex) {
      this.numberOfPartitions = numberOfPartitions;
      this.dataIterator = dataIterator;
      this.testIndex = testIndex;
    }
    
    public boolean hasNext() {
      
      if (isPoisened) {
        throw new IllegalStateException();
      }
      
      while (index % numberOfPartitions != testIndex) {
        if (dataIterator.hasNext()) {
          index++;
          dataIterator.next();
        }
        else {
          return false;
        }
      }
      
      return dataIterator.hasNext();
    }

    public E next() {
      
      if (isPoisened) {
        throw new IllegalStateException();
      }
      
      if (hasNext()) {
        index++;
        
        return dataIterator.next();
      }
      else {
        throw new NoSuchElementException();
      }
    }

    /**
     * Throws <code>UnsupportedOperationException</code>
     */
    public void remove() {
      throw new UnsupportedOperationException();
    }
    
    void poison() {
      isPoisened = true;
    }
  }
  
  /**
   * The <code>TrainingIterator</code> which iterates over
   * all training elements.
   * 
   * Note:
   * After the test <code>Iterator</code> was obtained
   * the training <code>Iterator</code> must not be used
   * anymore, otherwise a {@link IllegalStateException}
   * is thrown.
   * 
   * The iterators must not be used anymore after the
   * <code>CrossValidationPartitioner</code> was moved
   * to one of next partitions. If they are called anyway
   * a {@link IllegalStateException} is thrown.
   * 
   * @param <E>
   */
  public static class TrainingIterator<E> implements Iterator<E> {

    private ResetableIterator<E> dataIterator;
    
    private final int numberOfPartitions;
    
    private final int testIndex;
    
    private int index;
    
    private boolean isPoisened;
    
    private TestIterator<E> testIterator;
    
    TrainingIterator(ResetableIterator<E> dataIterator, int numberOfPartitions, int testIndex) {
      this.numberOfPartitions = numberOfPartitions;
      this.dataIterator = dataIterator;
      this.testIndex = testIndex;
    }
    
    /**
     * Checks if there is one more training element.
     * 
     * @throw IllegalStateException
     */
    public boolean hasNext() {
      
      if (testIterator != null || isPoisened) {
        throw new IllegalStateException();
      }
      
      // If the test element is reached skip over it to not include it in
      // the training data
      if (index % numberOfPartitions == testIndex) {
        if (dataIterator.hasNext()) {
          index++;
          dataIterator.next();
        }
        else {
          return false;
        }
      }
      
      return dataIterator.hasNext();
    }

    /**
     * Retrieves the next training element.
     * 
     * @throw IllegalStateException
     */
    public E next() {
      
      if (testIterator != null || isPoisened) {
        throw new IllegalStateException();
      }
      
      if (hasNext()) {
        
        E element = dataIterator.next();
        
        index++;
        
        return element;
      }
      else {
        throw new NoSuchElementException();
      }
    }

    /**
     * Throws <code>UnsupportedOperationException</code>
     */
    public void remove() {
      throw new UnsupportedOperationException();
    }
    
    void poison() {
      isPoisened = true;
      if (testIterator != null)
        testIterator.poison();
    }
    
    /**
     * Retrieves the <code>Iterator</code> over the test/evaluations
     * elements and poisons this <code>TrainingIterator</code>.
     * From now on calls to the hasNext and next methods are forbidden
     * and will raise an<code>IllegalArgumentException</code>.
     *  
     * @return
     */
    public Iterator<E> getTestIterator() {
      
      if (isPoisened) {
        throw new IllegalStateException();
      }
      
      if (testIterator == null) {
      
        dataIterator.reset();
        testIterator =  new TestIterator<E>(dataIterator, numberOfPartitions, testIndex);
      }
      
      return testIterator;
    }
  }
  
  /**
   * A {@link ResetableIterator} over a {@link Collection}.
   *
   * @param <T>
   */
  private static class ResetableCollectionIterator<E> implements ResetableIterator<E> {
    private Collection<E> collection;
    
    private Iterator<E> iterator;
    
    ResetableCollectionIterator(Collection<E> collection) {
      this.collection = collection;
      
      reset();
    }
    
    public void reset() {
      this.iterator = collection.iterator();
    }

    public boolean hasNext() {
      return iterator.hasNext();
    }

    public E next() {
      return iterator.next();
    }

    public void remove() {
      iterator.remove();
    }
  }
  
  /**
   * An <code>Iterator</code> over the whole set of data objects which
   * are used for the cross validation.
   */
  private ResetableIterator<E> dataIterator;
  
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
  private TrainingIterator<E> lastTrainingIterator;
  /**
   * Initializes the current instance.
   * 
   * @param inElements
   * @param numberOfPartitions
   */
  public CrossValidationPartitioner(ResetableIterator<E> inElements, int numberOfPartitions) {
    this.dataIterator = inElements;
    this.numberOfPartitions = numberOfPartitions;
  }
  
  /**
   * Initializes the current instance.
   * 
   * @param elements
   * @param numberOfPartitions
   */
  public CrossValidationPartitioner(Collection<E> elements, int numberOfPartitions) {
    this(new ResetableCollectionIterator<E>(elements), numberOfPartitions);
  }

  public Iterator<TrainingIterator<E>> iterator() {
    return this;
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
  public TrainingIterator<E> next() {
    if (hasNext()) {
      if (lastTrainingIterator != null)
        lastTrainingIterator.poison();
      
      dataIterator.reset();
      
      TrainingIterator<E> trainingIterator = new TrainingIterator<E>(dataIterator,
          numberOfPartitions, testIndex);
      
      testIndex++;
      
      lastTrainingIterator = trainingIterator;
      
      return trainingIterator;
    }
    else {
      throw new NoSuchElementException();
    }
  }

  public void remove() {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public String toString() {
    return "At partition" + Integer.toString(testIndex + 1) +
        " of " + Integer.toString(numberOfPartitions);
  }
}
