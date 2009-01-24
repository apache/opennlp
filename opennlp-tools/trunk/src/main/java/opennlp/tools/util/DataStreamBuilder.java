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

import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedList;

import opennlp.maxent.DataStream;

/**
 * This is a DataStream of elements contained in a collection.
 */
public final class DataStreamBuilder implements DataStream {

  private final Collection mData;

  private Iterator mDataIterator;

  private boolean mIsIterating;

  /**
   * Initializes the current instance.
   */
  public DataStreamBuilder() {
    mData = new LinkedList();

    mIsIterating = false;
  }

  /**
   * Initializes the current instance.
   *
   * @param object
   */
  public DataStreamBuilder(Object object) {
    this();

    add(object);
  }

  /**
   *Initializes the current instance.
   *
   * @param array
   */
  public DataStreamBuilder(Object[] array) {
    this();

    add(array);
  }

  /**
   * Initializes the current instance.
   *
   * @param data
   */
  public DataStreamBuilder(Collection data) {
    this();

    add(data);
  }

  /**
   * Adds the given data object.
   *
   * @param data
   */
  public void add(Object data) {
    checkIterating();

    mData.add(data);
  }

  /**
   * Adds the given array of data.
   *
   * @param data
   */
  public void add(Object[] data) {
    checkIterating();

    mData.addAll(Arrays.asList(data));
  }

  /**
   * Adds the given collection of data.
   *
   * @param data
   */
  public void add(Collection data) {
    checkIterating();

    mData.addAll(data);
  }

  private void checkIterating() {
   if (mIsIterating) {
     throw new ConcurrentModificationException(
         "Do not modify, after iterating started!");
   }
  }

  /**
   * Retrives the next token.
   */
  public Object nextToken() {
    mIsIterating = true;

    if (mDataIterator == null) {
      mDataIterator = mData.iterator();
    }

    return mDataIterator.next();
  }

  /**
   * Checks if one more token is available.
   */
  public boolean hasNext() {
    mIsIterating = true;

    if (mDataIterator == null) {
      mDataIterator = mData.iterator();
    }

    return mDataIterator.hasNext();
  }
}
