/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.dl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class InferenceOptionsValidationTest {

  @Test
  void testValidSplitOptions() {
    final InferenceOptions options = new InferenceOptions();
    options.setDocumentSplitSize(2);
    options.setSplitOverlapSize(1);

    assertDoesNotThrow(() -> AbstractDL.validateSplitOptions(options));
  }

  @Test
  void testRejectsZeroDocumentSplitSize() {
    final InferenceOptions options = new InferenceOptions();
    options.setDocumentSplitSize(0);

    assertThrows(IllegalArgumentException.class, () -> AbstractDL.validateSplitOptions(options));
  }

  @Test
  void testRejectsNegativeDocumentSplitSize() {
    final InferenceOptions options = new InferenceOptions();
    options.setDocumentSplitSize(-1);

    assertThrows(IllegalArgumentException.class, () -> AbstractDL.validateSplitOptions(options));
  }

  @Test
  void testRejectsNegativeSplitOverlapSize() {
    final InferenceOptions options = new InferenceOptions();
    options.setSplitOverlapSize(-1);

    assertThrows(IllegalArgumentException.class, () -> AbstractDL.validateSplitOptions(options));
  }

  @Test
  void testRejectsSplitOverlapSizeEqualToDocumentSplitSize() {
    final InferenceOptions options = new InferenceOptions();
    options.setDocumentSplitSize(2);
    options.setSplitOverlapSize(2);

    assertThrows(IllegalArgumentException.class, () -> AbstractDL.validateSplitOptions(options));
  }

  @Test
  void testRejectsSplitOverlapSizeGreaterThanDocumentSplitSize() {
    final InferenceOptions options = new InferenceOptions();
    options.setDocumentSplitSize(2);
    options.setSplitOverlapSize(3);

    assertThrows(IllegalArgumentException.class, () -> AbstractDL.validateSplitOptions(options));
  }
}
