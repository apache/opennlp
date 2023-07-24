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


import java.util.Objects;

/**
 * The context of a decision point during training.
 * This includes contextual predicates and an outcome.
 */
public class Event {
  private final String outcome;
  private final String[] context;
  private final float[] values;

  /**
   * Instantiates an {@link Event}.
   *
   * @param outcome The outcome to use. Must not be {@code null}.
   * @param context The {@link String array} of context elements. Must not be {@code null}.
   */
  public Event(String outcome, CharSequence[] context) {
    this(outcome,context,null);
  }

  /**
   * Instantiates an {@link Event}.
   *
   * @param outcome The outcome to use. Must not be {@code null}.
   * @param context The {@link String array} of context elements. Must not be {@code null}.
   * @param values The {@code float} array to use.
   */
  public Event(String outcome, String[] context, float[] values) {
    this.outcome = Objects.requireNonNull(outcome, "outcome must not be null");
    this.context = Objects.requireNonNull(context, "context must not be null");
    this.values = values;
  }

  /**
   * Instantiates an {@link Event}.
   *
   * @param outcome The outcome to use. Must not be {@code null}.
   * @param context The {@link CharSequence array} of context elements. Must not be {@code null}.
   * @param values The {@code float} array to use.
   */
  public Event(String outcome, CharSequence[] context, float[] values) {
    this.outcome = Objects.requireNonNull(outcome, "outcome must not be null");
    final String[] ctx = new String[context.length];
    for (int i = 0; i < context.length; i++) {
      ctx[i] = context[i].toString();
    }
    this.context = ctx;
    this.values = values;
  }

  /**
   * @return Retrieves the outcome.
   */
  public String getOutcome() {
    return outcome;
  }

  /**
   * @return Retrieves the {@link String array} of context elements.
   */
  public String[] getContext() {
    return context;
  }

  /**
   * @return Retrieves the {@code float} array.
   */
  public float[] getValues() {
    return values;
  }

  @Override
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
