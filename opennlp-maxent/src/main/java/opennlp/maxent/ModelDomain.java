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
 * A simple interface that represents a domain to which a particular maxent
 * model is primarily applicable. For instance, one might have a
 * part-of-speech tagger trained on financial text and another based on
 * children's stories.  This interface is used by the DomainToModelMap class
 * to allow an application to grab the models relevant for the different
 * domains.
 */
public interface ModelDomain {

  /**
   * Get the name of this domain.
   * 
   * @return The name of this domain.
   */
  public String getName();
}
