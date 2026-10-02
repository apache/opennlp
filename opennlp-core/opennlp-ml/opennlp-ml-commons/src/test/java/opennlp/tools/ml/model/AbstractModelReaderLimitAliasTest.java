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

package opennlp.tools.ml.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.ResourceLimits;

/**
 * Pins the deprecated limit aliases of {@link AbstractModelReader} to their
 * {@link ResourceLimits} source until they are removed.
 */
class AbstractModelReaderLimitAliasTest {

  @Test
  @SuppressWarnings("removal")
  void testAliasesMatchResourceLimits() {
    Assertions.assertEquals(ResourceLimits.MAX_ENTRIES, AbstractModelReader.MAX_ENTRIES);
    Assertions.assertEquals(ResourceLimits.MAX_ENTRIES_PROPERTY,
        AbstractModelReader.MAX_ENTRIES_PROPERTY);
  }
}
