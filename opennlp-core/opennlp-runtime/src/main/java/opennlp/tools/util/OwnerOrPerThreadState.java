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
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Routes mutable per-invocation state either to a single owner-thread field or to {@link ThreadLocal} storage
 * once multiple threads use the same enclosing instance.
 *
 * <p><b>Why not always {@link ThreadLocal}?</b> Frequently constructed components that are only used from one
 * thread would allocate or touch a {@code ThreadLocal} map on every access. The first thread uses a shared
 * {@code ownerState} until a second thread appears; then non-owner threads use lazily created per-thread
 * state.</p>
 *
 * <p>The {@code resetOwner} callback is invoked when the owner thread calls
 * {@link #clearForCurrentThread()} so owner fields can be restored to a clean state.</p>
 *
 * @param <S> mutable state type (e.g. token probabilities + spans)
 */
public final class OwnerOrPerThreadState<S> {

  private static final long NO_OWNER_THREAD = -1L;

  private final ThreadLocal<S> threadState;

  private final S ownerState;

  private final Object modeLock = new Object();

  private final AtomicLong ownerThreadId = new AtomicLong(NO_OWNER_THREAD);

  private volatile boolean multiThreaded;

  private final Consumer<S> resetOwner;

  /**
   * @param newState factory for one fresh state object (used for owner and for each thread)
   * @param resetOwner resets {@code ownerState} when the owner thread clears (must not clear other
   *     threads' state)
   */
  public OwnerOrPerThreadState(Supplier<S> newState, Consumer<S> resetOwner) {
    this.threadState = ThreadLocal.withInitial(newState);
    this.ownerState = newState.get();
    this.resetOwner = resetOwner;
  }

  /**
   * Returns the state object for the calling thread (owner or per-thread slot).
   *
   * @return mutable state for this thread
   */
  public S get() {
    long currentThreadId = Thread.currentThread().threadId();
    long owner = ownerThreadId.get();

    if (multiThreaded) {
      return owner == currentThreadId ? ownerState : threadState.get();
    }

    if (owner == currentThreadId) {
      return ownerState;
    }

    if (owner == NO_OWNER_THREAD
        && ownerThreadId.compareAndSet(NO_OWNER_THREAD, currentThreadId)) {
      return ownerState;
    }

    owner = ownerThreadId.get();
    if (owner == currentThreadId) {
      return ownerState;
    }

    synchronized (modeLock) {
      if (!multiThreaded && ownerThreadId.get() != currentThreadId) {
        multiThreaded = true;
      }
    }

    return ownerThreadId.get() == currentThreadId ? ownerState : threadState.get();
  }

  /**
   * Removes this thread's {@link ThreadLocal} slot and, if this thread is the owner, resets owner state and
   * releases ownership so a future single-thread user can claim it.
   */
  public void clearForCurrentThread() {
    threadState.remove();
    long currentThreadId = Thread.currentThread().threadId();
    if (ownerThreadId.get() == currentThreadId) {
      resetOwner.accept(ownerState);
      ownerThreadId.compareAndSet(currentThreadId, NO_OWNER_THREAD);
    }
  }
}
