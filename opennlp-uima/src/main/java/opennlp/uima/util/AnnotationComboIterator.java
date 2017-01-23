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

package opennlp.uima.util;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;

/**
 * UIMA Annotation iterator combination of super- and subiterator.
 *
 * <p>
 * This class supports a common idiom in UIMA annotation iteration, where you need to iterate over
 * two kinds of annotations in lock-step. For example, you often want to iterate over all sentences,
 * then do something on each sentence and all tokens in that sentence. Here's how to do this with
 * this class.
 *
 * <pre>
 * CAS cas = ...
 * Type sentenceType = ..., tokenType = ...
 * // Init with CAS, upper and lower type.
 * AnnotationComboIterator it = new AnnotationComboIterator(cas, sentenceType, tokenType);
 * // Iterate over sentences
 * for (AnnotationIteratorPair aiPair : it) {
 *   // Obtain sentence annotation
 *   AnnotationFS sentence = aiPair.getAnnotation();
 *   // Do something with sentence...
 *
 *   // Iterate over tokens
 *   for (AnnotationFS token : aiPair.getSubIterator()) {
 *     // Do something with tokens...
 *   }
 * }
 * </pre>
 *
 * The combo iterator returns in its <code>next()</code> method a pair of an annotation of the upper
 * type (e.g., sentence), and an iterator over annotations of the lower type (e.g., tokens). Note
 * that both the upper and lower iterator also implement the Iterable interface and can be use
 * directly in for-loops.
 *
 * <p>
 * Note that only this usage is safe. To keep the implementation efficient, the combo iterator keeps
 * two iterators internally that it increments in lock-step. Do not attempt, for example, to collect
 * more than one of the subiterators (token iterator in our example). Do not use this class if your
 * intended usage does not fall into this pattern.
 */
public class AnnotationComboIterator implements Iterable<AnnotationIteratorPair>,
    Iterator<AnnotationIteratorPair> {

  // Internal implementation of subiterator
  private class AnnotationIterator implements Iterable<AnnotationFS>, Iterator<AnnotationFS> {

    private AnnotationIterator() {
      super();
    }

    public AnnotationIterator iterator() {
      return this;
    }

    public boolean hasNext() {
      if (AnnotationComboIterator.this.nextLowerChecked) {
        return AnnotationComboIterator.this.nextLowerAvailable;
      }
      AnnotationComboIterator.this.nextLowerChecked = true;
      AnnotationComboIterator.this.nextLowerAvailable = false;
      if (AnnotationComboIterator.this.lowerIt.isValid()) {
        AnnotationFS lowerFS = AnnotationComboIterator.this.lowerIt.get();
        int lowerBegin = lowerFS.getBegin();
        while (lowerBegin < AnnotationComboIterator.this.upperBegin) {
          AnnotationComboIterator.this.lowerIt.moveToNext();
          if (AnnotationComboIterator.this.lowerIt.isValid()) {
            lowerFS = AnnotationComboIterator.this.lowerIt.get();
            lowerBegin = lowerFS.getBegin();
          } else {
            return false;
          }
        }
        if (AnnotationComboIterator.this.upperEnd >= lowerFS.getEnd()) {
          AnnotationComboIterator.this.nextLowerAvailable = true;
        }
      }
      return AnnotationComboIterator.this.nextLowerAvailable;
    }

    public AnnotationFS next() {
      if (AnnotationComboIterator.this.nextLowerChecked) {
        if (!AnnotationComboIterator.this.nextLowerAvailable) {
          throw new NoSuchElementException();
        }
      } else if (!hasNext()) {
        throw new NoSuchElementException();
      }
      AnnotationComboIterator.this.nextLowerChecked = false;
      final AnnotationFS rv = AnnotationComboIterator.this.lowerIt.get();
      AnnotationComboIterator.this.lowerIt.moveToNext();
      return rv;
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }

  }

  // The upper iterator (e.g., sentence iterator)
  private final FSIterator<AnnotationFS> upperIt;

  // The lower iterator (e.g., token iterator)
  private final FSIterator<AnnotationFS> lowerIt;

  // Start position of current upper (e.g., sentence) annotation. Together with the end position,
  // this determines the boundaries for the lower iterator.
  private int upperBegin;

  // End position of current upper annotation.
  private int upperEnd;

  // Have we already checked that a next lower annotation is available? Premature optimization...
  private boolean nextLowerChecked = false;

  // State variable that holds the status of the lower iterator only in case that nextLowerChecked
  // is true.
  private boolean nextLowerAvailable = false;

  /**
   * Create a new combo iterator.
   *
   * @param cas
   *          The CAS we're operating on.
   * @param upper
   *          The type of the upper iterator, e.g., sentence.
   * @param lower
   *          The type of the lower iterator, e.g., token.
   */
  public AnnotationComboIterator(CAS cas, Type upper, Type lower) {
    this.upperIt = cas.getAnnotationIndex(upper).iterator();
    this.lowerIt = cas.getAnnotationIndex(lower).iterator();
    this.upperIt.moveToFirst();
    this.lowerIt.moveToFirst();
    if (this.upperIt.isValid()) {
      final AnnotationFS upperFS = this.upperIt.get();
      this.upperBegin = upperFS.getBegin();
      this.upperEnd = upperFS.getEnd();
    } else {
      this.nextLowerChecked = true;
    }
  }

  public boolean hasNext() {
    return this.upperIt.hasNext();
  }

  public AnnotationIteratorPair next() {
    if (!this.upperIt.hasNext()) {
      throw new NoSuchElementException();
    }
    final AnnotationFS upperFS = this.upperIt.next();
    this.upperBegin = upperFS.getBegin();
    this.upperEnd = upperFS.getEnd();
    this.nextLowerChecked = false;
    return new AnnotationIteratorPair(upperFS, new AnnotationIterator());
  }

  public Iterator<AnnotationIteratorPair> iterator() {
    return this;
  }

  /**
   * Not supported.
   */
  public void remove() {
    throw new UnsupportedOperationException();
  }

}
