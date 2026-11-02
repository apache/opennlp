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

package org.example.extension;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test helper that resides outside the {@code opennlp.} package and records whether
 * {@link StaticInitProbe} has been initialized.
 */
public final class StaticInitMarker {

  /**
   * Set to {@code true} by the static initializer of {@link StaticInitProbe}.
   */
  public static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

  private StaticInitMarker() {
  }
}
