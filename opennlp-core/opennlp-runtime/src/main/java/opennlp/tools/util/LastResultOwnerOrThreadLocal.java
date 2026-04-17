/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.util;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Stores a per-thread "last result" value for APIs that expose probabilities or other data from
 * the most recent decode call (e.g. {@code tag()} then {@code probs()}).
 *
 * <p><b>Why not always {@link ThreadLocal}?</b> Short-lived instances that are only ever used from one thread
 * would pay for a {@code ThreadLocal} map entry on every {@link #set(Object)}. This type keeps the first
 * thread's value in plain fields until a second thread touches the same instance; then it switches non-owner
 * threads to {@code ThreadLocal} storage. Once the instance has gone multi-threaded, it stays that way for
 * the rest of its lifetime (one-way transition).</p>
 *
 * <p><b>Thread identity is tracked by {@link Thread#threadId() thread id} (a {@code long}), not by
 * {@code Thread} reference.</b> Holding a strong reference to a worker thread in a long-lived component
 * pins the thread's context classloader in container environments (e.g. Jakarta EE) — exactly the leak this
 * class is designed to avoid. Thread ids can be recycled after a thread terminates, so the worst-case
 * outcome is that a recycled-id thread sees a stale {@code ownerValue} from a previous thread instead of
 * {@code null}; that is no worse than the documented contract for {@link #get()} (callers are expected to
 * call {@link #set(Object)} before {@link #get()} on every thread).</p>
 *
 * <p>Call {@link #clearForCurrentThread()} when releasing pooled threads or disposing the enclosing component
 * to avoid classloader retention.</p>
 *
 * <h2>Relationship to other thread-safety patterns in this PR</h2>
 *
 * <p>OpenNLP's ME classes use three different strategies depending on what they need to share between
 * {@code decode()} and {@code probs()} on the same thread:</p>
 * <ul>
 *   <li>{@code LastResultOwnerOrThreadLocal} / {@link OwnerOrPerThreadState} — used where the public API
 *       exposes per-thread last-result state ({@code POSTaggerME}, {@code SentenceDetectorME},
 *       {@code TokenizerME}). The owner-fast-path keeps single-threaded callers cheap.</li>
 *   <li>{@code volatile} field plus method-local processing plus an atomic publish at the end — used where
 *       {@code decode()} returns the result directly and there is no separate {@code probs()}-style accessor
 *       ({@code ChunkerME}, {@code LemmatizerME}, {@code NameFinderME}).</li>
 *   <li>Plain {@link ThreadLocal} — used inside the cache layers ({@code BeamSearch},
 *       {@code CachedFeatureGenerator}, {@code ConfigurablePOSContextGenerator}) where the per-call cost of
 *       a {@code ThreadLocal.get()} is dwarfed by the work it guards (e.g. {@code MaxentModel.eval}).</li>
 * </ul>
 *
 * @param <T> the type of the stored value (often a decode result such as {@link Sequence})
 */
public final class LastResultOwnerOrThreadLocal<T> {

  private static final long NO_OWNER_THREAD = -1L;

  private final ThreadLocal<T> threadValue = new ThreadLocal<>();

  private final Object ownerLock = new Object();

  private final AtomicLong ownerThreadId = new AtomicLong(NO_OWNER_THREAD);

  private volatile boolean multiThreaded;

  /**
   * The owner thread's last value. Only the owner thread reads or writes this field, so program order
   * gives the visibility guarantee we need without {@code volatile}.
   */
  private T ownerValue;

  /**
   * Records {@code value} as the last result for the calling thread.
   *
   * @param value the value to store (typically non-null after a successful decode)
   */
  public void set(T value) {
    final long currentThreadId = Thread.currentThread().threadId();

    if (multiThreaded) {
      if (ownerThreadId.get() == currentThreadId) {
        ownerValue = value;
      } else {
        threadValue.set(value);
      }
      return;
    }

    long owner = ownerThreadId.get();
    if (owner == NO_OWNER_THREAD
        && ownerThreadId.compareAndSet(NO_OWNER_THREAD, currentThreadId)) {
      ownerValue = value;
      return;
    }

    if (ownerThreadId.get() == currentThreadId) {
      ownerValue = value;
      return;
    }

    synchronized (ownerLock) {
      if (!multiThreaded && ownerThreadId.get() != currentThreadId) {
        multiThreaded = true;
      }
    }

    if (ownerThreadId.get() == currentThreadId) {
      ownerValue = value;
    } else {
      threadValue.set(value);
    }
  }

  /**
   * Returns the last stored value for the calling thread, or {@code null} if none.
   *
   * @return last value for this thread, or {@code null}
   */
  public T get() {
    final long currentThreadId = Thread.currentThread().threadId();

    if (multiThreaded) {
      return ownerThreadId.get() == currentThreadId ? ownerValue : threadValue.get();
    }

    long owner = ownerThreadId.get();
    if (owner == NO_OWNER_THREAD) {
      return null;
    }
    if (owner == currentThreadId) {
      return ownerValue;
    }

    synchronized (ownerLock) {
      if (!multiThreaded && ownerThreadId.get() != currentThreadId) {
        multiThreaded = true;
      }
    }

    return ownerThreadId.get() == currentThreadId ? ownerValue : threadValue.get();
  }

  /**
   * Clears {@link ThreadLocal} state for the current thread and, when this thread is the single-thread owner,
   * clears owner fields so another thread can become owner.
   */
  public void clearForCurrentThread() {
    threadValue.remove();
    final long currentThreadId = Thread.currentThread().threadId();
    if (ownerThreadId.get() == currentThreadId) {
      ownerValue = null;
      synchronized (ownerLock) {
        if (!multiThreaded) {
          ownerThreadId.compareAndSet(currentThreadId, NO_OWNER_THREAD);
        }
      }
    }
  }
}
