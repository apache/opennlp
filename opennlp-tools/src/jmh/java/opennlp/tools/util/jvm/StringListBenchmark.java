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
import org.openjdk.jmh.infra.Blackhole;

import opennlp.tools.util.jvm.jmh.ExecutionPlan;
import opennlp.tools.util.StringList;

/**
 * A benchmark class to test different implementation of {@link StringList} within OpenNLP
 */
public class StringListBenchmark {

  @Benchmark
  public void newWithArrayConstructor(Blackhole blackhole, ExecutionPlan exec) {
    blackhole.consume(new StringList(exec.strings));
  }

}
