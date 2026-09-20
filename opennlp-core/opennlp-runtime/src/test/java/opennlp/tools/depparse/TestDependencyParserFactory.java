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

package opennlp.tools.depparse;

import java.util.Map;

import opennlp.tools.util.InvalidFormatException;

/** A factory whose manifest entry must survive model persistence. */
public class TestDependencyParserFactory extends DependencyParserFactory {

  /** {@inheritDoc} */
  @Override
  public Map<String, String> createManifestEntries() {
    return Map.of("test-entry", "test-value");
  }

  /** {@inheritDoc} */
  @Override
  public void validateArtifactMap() throws InvalidFormatException {
    super.validateArtifactMap();
    if (!"test-value".equals(artifactProvider.getManifestProperty("test-entry"))) {
      throw new InvalidFormatException("Missing factory manifest entry");
    }
  }
}
