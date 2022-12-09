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

package opennlp.tools.chunker;

import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.SequenceValidator;
import opennlp.tools.util.TokenTag;
import opennlp.tools.util.ext.ExtensionLoader;

public class ChunkerFactory extends BaseToolFactory {

  /**
   * Creates a {@link ChunkerFactory} that provides the default implementation
   * of the resources.
   */
  public ChunkerFactory() {
  }

  /**
   * Instantiates a {@link ChunkerFactory} via a given {@code subclassName}.
   *
   * @param subclassName The class name used for instantiation. If {@code null}, an
   *                     instance of {@link ChunkerFactory} will be returned
   *                     per default. Otherwise, the {@link ExtensionLoader} mechanism
   *                     is applied to load the requested {@code subclassName}.
   *
   * @return A valid {@link ChunkerFactory} instance.
   * @throws InvalidFormatException Thrown if the {@link ExtensionLoader} mechanism failed to
   *                                create the factory associated with {@code subclassName}.
   */
  public static ChunkerFactory create(String subclassName)
      throws InvalidFormatException {
    if (subclassName == null) {
      // will create the default factory
      return new ChunkerFactory();
    }
    try {
      return ExtensionLoader.instantiateExtension(ChunkerFactory.class, subclassName);
    } catch (Exception e) {
      String msg = "Could not instantiate the " + subclassName
          + ". The initialization threw an exception.";
      throw new InvalidFormatException(msg, e);
    }
  }

  @Override
  public void validateArtifactMap() throws InvalidFormatException {
    // no additional artifacts
  }

  public SequenceValidator<TokenTag> getSequenceValidator() {
    return new DefaultChunkerSequenceValidator();
  }

  public ChunkerContextGenerator getContextGenerator() {
    return new DefaultChunkerContextGenerator();
  }
}
