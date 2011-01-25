/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


package opennlp.maxent;

/**
 * A pool of read-only, unsigned Integer objects within a fixed,
 * non-sparse range.  Use this class for operations in which a large
 * number of Integer wrapper objects will be created.
 */
public class IntegerPool {
    private Integer[] _table;

  /**
   * Creates an IntegerPool with 0..size Integer objects.
   * 
   * @param size
   *          the size of the pool.
   */
  public IntegerPool(int size) {
    _table = new Integer[size];
    for (int i = 0; i < size; i++) {
      _table[i] = new Integer(i);
    } // end of for (int i = 0; i < size; i++)
  }

  /**
   * Returns the shared Integer wrapper for <tt>value</tt> if it is inside the
   * range managed by this pool. if <tt>value</tt> is outside the range, a new
   * Integer instance is returned.
   * 
   * @param value
   *          an <code>int</code> value
   * @return an <code>Integer</code> value
   */
  public Integer get(int value) {
    if (value < _table.length && value >= 0) {
      return _table[value];
    } else {
      return new Integer(value);
    }
  }
}
