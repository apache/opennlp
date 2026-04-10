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

package opennlp.tools.chunker;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.ml.Probabilistic;
import opennlp.tools.util.Sequence;
import opennlp.tools.util.Span;

/**
 * A thread-safe version of the {@link ChunkerME}. Using it is completely transparent.
 * You can use it in a single-threaded context as well, it only incurs a minimal overhead.
 * <p>
 * <b>Note:</b> This class is deprecated and now delegates to a single shared
 * {@link ChunkerME} instance, which is thread-safe as of OPENNLP-1816.
 * Calling {@link #close()} clears the current thread's thread-local state for compatibility.
 *
 * @see Chunker
 * @see ChunkerME
 * @see Probabilistic
 *
 * @deprecated As of OPENNLP-1816, {@link ChunkerME} is
 *     itself thread-safe. Use it directly instead.
 */
@Deprecated(since = "3.0.0")
@ThreadSafe
public class ThreadSafeChunkerME implements Chunker, Probabilistic, AutoCloseable {

  private final ChunkerME sharedChunker;

  /**
   * Initializes a {@link ThreadSafeChunkerME} with the specified {@code model}.
   *
   * @param model A valid {@link ChunkerModel}.
   */
  public ThreadSafeChunkerME(ChunkerModel model) {
    super();
    this.sharedChunker = new ChunkerME(model);
  }

  @Override
  public String[] chunk(String[] toks, String[] tags) {
    return sharedChunker.chunk(toks, tags);
  }

  @Override
  public Span[] chunkAsSpans(String[] toks, String[] tags) {
    return sharedChunker.chunkAsSpans(toks, tags);
  }

  @Override
  public Sequence[] topKSequences(String[] sentence, String[] tags) {
    return sharedChunker.topKSequences(sentence, tags);
  }

  @Override
  public Sequence[] topKSequences(String[] sentence, String[] tags, double minSequenceScore) {
    return sharedChunker.topKSequences(sentence, tags, minSequenceScore);
  }

  @Override
  public void close() {
    sharedChunker.clearThreadLocalState();
  }

  @Override
  public double[] probs() {
    return sharedChunker.probs();
  }
}
