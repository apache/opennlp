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
import opennlp.tools.util.Sequence;
import opennlp.tools.util.Span;

/**
 * A thread-safe version of the {@link ChunkerME}. Using it is completely transparent.
 * You can use it in a single-threaded context as well, it only incurs a minimal overhead.
 *
 * @implNote
 * This implementation uses a {@link ThreadLocal}. Although the implementation is
 * lightweight because the model is not duplicated, if you have many long-running threads,
 * you may run into memory problems.
 * <p>
 * Be careful when using this in a Jakarta EE application, for example.
 * </p>
 * The user is responsible for clearing the {@link ThreadLocal}.
 *
 * @see Chunker
 * @see ChunkerME
 */
@ThreadSafe
public class ThreadSafeChunkerME implements Chunker, AutoCloseable {

  private final ChunkerModel model;

  private final ThreadLocal<ChunkerME> threadLocal = new ThreadLocal<>();

  /**
   * Initializes a {@link ThreadSafeChunkerME} with the specified {@code model}.
   *
   * @param model A valid {@link ChunkerModel}.
   */
  public ThreadSafeChunkerME(ChunkerModel model) {
    super();
    this.model = model;
  }

  private ChunkerME getChunker() {
    ChunkerME c = threadLocal.get();
    if (c == null) {
      c = new ChunkerME(model);
      threadLocal.set(c);
    }
    return c;
  }

  @Override
  public String[] chunk(String[] toks, String[] tags) {
    return getChunker().chunk(toks, tags);
  }

  @Override
  public Span[] chunkAsSpans(String[] toks, String[] tags) {
    return getChunker().chunkAsSpans(toks, tags);
  }

  @Override
  public Sequence[] topKSequences(String[] sentence, String[] tags) {
    return getChunker().topKSequences(sentence, tags);
  }

  @Override
  public Sequence[] topKSequences(String[] sentence, String[] tags, double minSequenceScore) {
    return getChunker().topKSequences(sentence, tags, minSequenceScore);
  }

  @Override
  public void close() {
    threadLocal.remove();
  }

}
