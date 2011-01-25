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

/**
 * An interface for objects which read events during training.
 */
public interface EventCollector {

  /**
   * Return the events which this EventCollector has gathered. It must get its
   * data from a constructor.
   * 
   * @return the events that this EventCollector has gathered
   */
  public Event[] getEvents();

  /**
   * Return the events which this EventCollector has gathered based on whether
   * we wish to train a model or evaluate one based on those events.
   * 
   * @param evalMode
   *          true if we are evaluating based on the events, false if we are
   *          training.
   * @return the events that this EventCollector has gathered
   */
  public Event[] getEvents(boolean evalMode);
}
