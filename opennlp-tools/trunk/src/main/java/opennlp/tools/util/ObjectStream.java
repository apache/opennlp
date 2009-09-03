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

/**
 * Reads <code>Object</code>s from a stream.
 * 
 * @see ObjectStreamException
 */
public interface ObjectStream<T> {
  
  /**
   * Returns the next object. Calling this method repeatedly until it returns
   * null will return each object from the underlying source exactly once. 
   * 
   * @return the next object or null to signal that the stream is exhausted
   */
  T read() throws ObjectStreamException;
  
  /**
   * Repositions the stream at the beginning and the previously seen object sequence
   * will be repeated exactly. This method can be used to re-read
   * the stream if multiple passes over the objects are required.
   * 
   * The implementation of this method is optional.
   */
  void reset() throws ObjectStreamException, UnsupportedOperationException;
  
  /**
   * Closes the <code>ObjectStream</code> and releases all allocated
   * resources. After close was called its not allowed to call
   * read or reset.
   * 
   * @throws ObjectStreamException
   */
  void close() throws ObjectStreamException;
}
