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

package opennlp.tools.ml.maxent;

/**
 * An interface for objects which can deliver a stream of training data to be
 * supplied to an EventStream. It is not necessary to use a {@link DataStream} in a
 * Maxent application, but it can be used to support a wider variety of formats
 * in which your training data can be held.
 */
public interface DataStream {

  /**
   * Returns the next slice of data held in this {@link DataStream}.
   *
   * @return The Object representing the data which is next in this {@link DataStream}.
   */
  Object nextToken();

  /**
   * Test whether there are any events remaining in this {@link DataStream}.
   *
   * @return {@code true} if this {@link DataStream} has more data tokens, {@code false} otherwise.
   */
  boolean hasNext();
}

