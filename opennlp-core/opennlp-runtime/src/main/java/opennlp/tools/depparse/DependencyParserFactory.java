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

import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ext.ExtensionLoader;

/**
 * Provides the artifacts, serializers and manifest entries of a dependency model.
 * Subclasses must provide a public no-argument constructor for model loading.
 *
 * @since 3.0.0
 */
public class DependencyParserFactory extends BaseToolFactory {

  /** Creates the default factory, which requires no additional artifacts. */
  public DependencyParserFactory() {
  }

  /**
   * Creates the default factory or a named extension.
   *
   * @param subclassName The extension class name, or {@code null} for the default.
   * @return The factory.
   * @throws InvalidFormatException If the named extension cannot be instantiated.
   */
  public static DependencyParserFactory create(String subclassName) throws InvalidFormatException {
    if (subclassName == null) {
      return new DependencyParserFactory();
    }
    try {
      return ExtensionLoader.instantiateExtension(DependencyParserFactory.class, subclassName);
    } catch (Exception e) {
      throw new InvalidFormatException("Could not instantiate dependency parser factory: "
          + subclassName, e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void validateArtifactMap() throws InvalidFormatException {
    // The default factory has no additional artifacts to validate.
  }
}
