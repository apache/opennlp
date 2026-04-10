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

/**
 * Stores a per-thread "last result" value for APIs that expose probabilities or other data from
 * the most recent decode call (e.g. {@code tag()} then {@code probs()}).
 *
 * <p><b>Why not always {@link ThreadLocal}?</b> Short-lived instances that are only ever used from one thread
 * would pay for a {@code ThreadLocal} map entry on every {@link #set(Object)}. This type keeps the first
 * thread's value in plain fields until a second thread touches the same instance; then it switches non-owner
 * threads to {@code ThreadLocal} storage.</p>
 *
 * <p>Call {@link #clearForCurrentThread()} when releasing pooled threads or disposing the enclosing component
 * to avoid classloader retention.</p>
 *
 * @param <T> the type of the stored value (often a decode result such as {@link Sequence})
 */
public final class LastResultOwnerOrThreadLocal<T> {

  private final ThreadLocal<T> threadValue = new ThreadLocal<>();

  private final Object ownerLock = new Object();

  private volatile Thread ownerThread;

  private volatile boolean multiThreaded;

  private T ownerValue;

  /**
   * Records {@code value} as the last result for the calling thread.
   *
   * @param value the value to store (typically non-null after a successful decode)
   */
  public void set(T value) {
    Thread current = Thread.currentThread();

    if (multiThreaded) {
      if (current == ownerThread) {
        ownerValue = value;
      } else {
        threadValue.set(value);
      }
      return;
    }

    Thread owner = ownerThread;
    if (owner == null) {
      synchronized (ownerLock) {
        if (!multiThreaded && ownerThread == null) {
          ownerThread = current;
          ownerValue = value;
          return;
        }
        owner = ownerThread;
      }
    }

    if (!multiThreaded && current == owner) {
      ownerValue = value;
      return;
    }

    synchronized (ownerLock) {
      if (!multiThreaded && ownerThread != current) {
        multiThreaded = true;
      }
    }

    if (current == ownerThread) {
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
    Thread current = Thread.currentThread();

    if (multiThreaded) {
      return current == ownerThread ? ownerValue : threadValue.get();
    }

    Thread owner = ownerThread;
    if (owner == null) {
      return null;
    }
    if (current == owner) {
      return ownerValue;
    }

    synchronized (ownerLock) {
      if (!multiThreaded && ownerThread != current) {
        multiThreaded = true;
      }
    }

    return current == ownerThread ? ownerValue : threadValue.get();
  }

  /**
   * Clears {@link ThreadLocal} state for the current thread and, when this thread is the single-thread owner,
   * clears owner fields so another thread can become owner.
   */
  public void clearForCurrentThread() {
    threadValue.remove();
    Thread current = Thread.currentThread();
    if (current == ownerThread) {
      ownerValue = null;
      synchronized (ownerLock) {
        if (!multiThreaded && ownerThread == current) {
          ownerThread = null;
        }
      }
    }
  }
}
