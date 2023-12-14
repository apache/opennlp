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
package opennlp.tools.jmh;

import java.util.Random;

import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class ExecutionPlan {

  private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
  private static final Random RANDOM = new Random(42);

  public final String[] strings = new String[1_000_000];

  @Setup(Level.Invocation)
  public void setUp() {
    for (int i = 0; i < 1_000_000; i++) {
      strings[i] = generateRandomString(15);
    }
  }

  private static String generateRandomString(int length) {
    final StringBuilder randomString = new StringBuilder();

    for (int i = 0; i < length; i++) {
      int index = RANDOM.nextInt(CHARS.length());
      randomString.append(CHARS.charAt(index));
    }

    return randomString.toString();
  }
}