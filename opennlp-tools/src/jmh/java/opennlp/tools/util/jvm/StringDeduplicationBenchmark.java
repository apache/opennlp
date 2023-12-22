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
package opennlp.tools.util.jvm;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/**
 * A benchmark class / setup by Aleksey Shipilëv.
 * Resides here to investigate performance of String deduplication approaches
 * on different environments.
 * <p>
 * Origin:
 * <a href="https://shipilev.net/jvm/anatomy-quarks/10-string-intern/">
 *   https://shipilev.net/jvm/anatomy-quarks/10-string-intern/</a>
 * <p>
 * His conclusion:<br>
 * "Do not use String.intern() without thinking very hard about it, okay?"
 */
@State(Scope.Benchmark)
public class StringDeduplicationBenchmark {

  @Param({"1", "100", "10000", "1000000"})
  private int size;

  private JvmStringInterner str;
  private CHMStringInterner chm;
  private HMStringInterner hm;
  private CHMStringDeduplicator chmd05;
  private NoOpStringInterner noop;

  @Setup
  public void setup() {
    str = new JvmStringInterner();
    chm = new CHMStringInterner();
    hm = new HMStringInterner();
    chmd05 = new CHMStringDeduplicator();
    noop = new NoOpStringInterner();
  }

  @Benchmark
  public void intern(Blackhole bh) {
    for (int c = 0; c < size; c++) {
      bh.consume(str.intern("String" + c));
    }
  }

  @Benchmark
  public void chm(Blackhole bh) {
    for (int c = 0; c < size; c++) {
      bh.consume(chm.intern("String" + c));
    }
  }

  @Benchmark
  public void hm(Blackhole bh) {
    for (int c = 0; c < size; c++) {
      bh.consume(hm.intern("String" + c));
    }
  }

  @Benchmark
  public void chmd05(Blackhole bh) {
    for (int c = 0; c < size; c++) {
      bh.consume(chmd05.intern("String" + c));
    }
  }

  @Benchmark
  public void noop(Blackhole bh) {
    for (int c = 0; c < size; c++) {
      bh.consume(noop.intern("String" + c));
    }
  }
}
