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
package opennlp.dl.vectors;

import java.io.IOException;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.profile.InternalProfiler;
import org.openjdk.jmh.results.AggregationPolicy;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.ScalarResult;

/**
 * Reports the memory a benchmark holds outside the Java heap at the end of every iteration: the
 * bytes and the number of the JVM's direct buffers, and on Linux the resident set size of the
 * process, which includes the memory ONNX Runtime allocates natively. Each is aggregated as the
 * maximum over the iterations.
 *
 * <p>Use it with {@code -prof opennlp.dl.vectors.NativeMemoryProfiler}.</p>
 */
public class NativeMemoryProfiler implements InternalProfiler {

  private static final Path STATUS = Path.of("/proc/self/status");

  private static final String RSS = "VmRSS:";

  @Override
  public String getDescription() {
    return "Direct buffers and the resident set size at the end of every iteration";
  }

  @Override
  public void beforeIteration(final BenchmarkParams benchmarkParams,
      final IterationParams iterationParams) {
  }

  @Override
  public Collection<? extends Result> afterIteration(final BenchmarkParams benchmarkParams,
      final IterationParams iterationParams, final IterationResult result) {
    final List<Result> results = new ArrayList<>();
    for (final BufferPoolMXBean pool
        : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
      if ("direct".equals(pool.getName())) {
        results.add(new ScalarResult("direct.used", pool.getMemoryUsed(), "bytes",
            AggregationPolicy.MAX));
        results.add(new ScalarResult("direct.count", pool.getCount(), "buffers",
            AggregationPolicy.MAX));
      }
    }
    final long rss = residentSetSize();
    if (rss >= 0) {
      results.add(new ScalarResult("rss", rss, "bytes", AggregationPolicy.MAX));
    }
    return results;
  }

  /**
   * {@return the resident set size of this process in bytes, or {@code -1} where
   * {@code /proc/self/status} does not report it}
   */
  private long residentSetSize() {
    if (!Files.isReadable(STATUS)) {
      return -1;
    }
    try {
      for (final String line : Files.readAllLines(STATUS, StandardCharsets.UTF_8)) {
        if (line.startsWith(RSS)) {
          // The line reads "VmRSS:" followed by the size in kB.
          long kilobytes = 0;
          for (int i = RSS.length(); i < line.length(); i++) {
            final char c = line.charAt(i);
            if (c >= '0' && c <= '9') {
              kilobytes = kilobytes * 10 + (c - '0');
            }
          }
          return kilobytes * 1024;
        }
      }
    } catch (final IOException e) {
      return -1;
    }
    return -1;
  }
}
