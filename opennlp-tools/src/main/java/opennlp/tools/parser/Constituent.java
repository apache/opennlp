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


package opennlp.tools.parser;

import opennlp.tools.util.Span;

/**
 * Class used to hold constituents when reading parses.
 */
public class Constituent {

  private String label;
  private Span span;

  public Constituent(String label, Span span) {
    this.label = label;
    this.span = span;
  }


  /**
   * Returns the label of the constituent.
   * @return the label of the constituent.
   */
  public String getLabel() {
    return label;
  }


  /**
   * Assigns the label to the constituent.
   * @param label The label to set.
   */
  public void setLabel(String label) {
    this.label = label;
  }


  /**
   * Returns the span of the constituent.
   * @return the span of the constituent.
   */
  public Span getSpan() {
    return span;
  }
}
