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

import java.util.HashMap;
import java.util.Map;

import opennlp.tools.commons.Internal;

/**
 * A {@link StringInterner} implementation based on {@link HashMap} by Aleksey Shipilëv.
 * This implementation is <b>not</b> thread-safe.
 * <p>
 * Origin:
 * <a href="https://shipilev.net/jvm/anatomy-quarks/10-string-intern/">
 * https://shipilev.net/jvm/anatomy-quarks/10-string-intern/</a>
 */
@Internal
class HMStringInterner implements StringInterner {

  private final Map<String, String> map;

  public HMStringInterner() {
    map = new HashMap<>();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String intern(String sample) {
    final String exist = map.putIfAbsent(sample, sample);
    return (exist == null) ? sample : exist;
  }
}
