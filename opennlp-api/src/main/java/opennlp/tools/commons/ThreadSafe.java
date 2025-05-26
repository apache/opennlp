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

package opennlp.tools.commons;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Classes, fields, or methods annotated {@code &#64;ThreadSafe} are safe to use
 * in multithreading contexts. In general, classes that adhere to (one of)
 * the following concepts:
 * <ul>
 *  <li>Statelessness: <b>no</b> methods relies on external state or
 *      maintain state at all,</li>
 *  <li>Immutability: <b>all</b> attributes are {@code final} so that
 *      internal state can't be modified at all,</li>
 *  <li>Thread locality: non-{@code final} fields are <b>only</b>
 *      accessed via an independently initialized copy,</li>
 *  <li>Atomicity: <b>all</b> operations on fields are perform via
 *      {@link java.util.concurrent.atomic atomic data types}, or</li>
 *  <li>Synchronization: <b>all</b> non-final methods, fields, or
 *      method local variables are manipulated via locks, that is, in a
 *      {@code synchronized} manner</li>
 * </ul>
 * are safe to use from multiple threads. In addition, thread-safety can be
 * achieved by using the concepts of either
 * <ul>
 *   <li>{@link java.util.concurrent.locks.ReentrantLock ReentrantLock}, or
 *   <li>{@link java.util.concurrent.locks.ReadWriteLock ReadWriteLock}
 * </ul>
 * in which a thread acquires the lock for write operations and protects
 * the locked object or data element from changes until the lock is released.
 */
@Documented
@Retention(value = RetentionPolicy.RUNTIME)
public @interface ThreadSafe {

  /**
   * The OpenNLP release when an element was first declared {@code thread-safe}.
   */
  String since() default "";
}
