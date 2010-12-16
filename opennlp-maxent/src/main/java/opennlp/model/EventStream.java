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

package opennlp.model;

import java.io.IOException;

/**
 * A object which can deliver a stream of training events for the GIS procedure
 * (or others such as IIS if and when they are implemented). EventStreams don't
 * need to use opennlp.maxent.DataStreams, but doing so would provide greater
 * flexibility for producing events from data stored in different formats.
 */
public interface EventStream {

  /**
   * Returns the next Event object held in this EventStream.
   * 
   * @return the Event object which is next in this EventStream
   */
  public Event next() throws IOException;

  /**
   * Test whether there are any Events remaining in this EventStream.
   * 
   * @return true if this EventStream has more Events
   */
  public boolean hasNext() throws IOException;

}
