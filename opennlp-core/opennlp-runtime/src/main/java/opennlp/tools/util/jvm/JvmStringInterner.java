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

import opennlp.tools.commons.Internal;
import opennlp.tools.commons.ThreadSafe;

/**
 * A {@link StringInterner} implementation based on {@code String.intern()}. Strings interned via
 * this implementation are put into PermGen space. If needed, the PermGen memory can be increased by
 * the JVM argument {@code -XX:MaxPermSize}.
 * <p>
 * Using this {@link StringInterner} brings back the default behaviour of OpenNLP before version
 * {@code 2.3.2}. You can use it by setting the system property {@code opennlp.interner.class} to
 * the fully qualified classname of a {@link StringInterner} implementation.
 * </p>
 */
@Internal
@ThreadSafe
class JvmStringInterner implements StringInterner {

  /**
   * {@inheritDoc}
   */
  @Override
  public String intern(String sample) {
    return sample.intern();
  }
}
