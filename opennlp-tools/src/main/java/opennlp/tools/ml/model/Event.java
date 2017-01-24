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

package opennlp.tools.ml.model;


/**
 * The context of a decision point during training.  This includes
 * contextual predicates and an outcome.
 */
public class Event {
  private String outcome;
  private String[] context;
  private float[] values;

  public Event(String outcome, String[] context) {
    this(outcome,context,null);
  }

  public Event(String outcome, String[] context, float[] values) {
    this.outcome = outcome;
    this.context = context;
    this.values = values;
  }

  public String getOutcome() {
    return outcome;
  }

  public String[] getContext() {
    return context;
  }

  public float[] getValues() {
    return values;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(outcome).append(" [");
    if (context.length > 0) {
      sb.append(context[0]);
      if (values != null) {
        sb.append("=").append(values[0]);
      }
    }
    for (int ci = 1; ci < context.length; ci++) {
      sb.append(" ").append(context[ci]);
      if (values != null) {
        sb.append("=").append(values[ci]);
      }
    }
    sb.append("]");
    return sb.toString();
  }
}
