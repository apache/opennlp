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

package opennlp.tools.lemmatizer;

import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.SequenceValidator;
import opennlp.tools.util.ext.ExtensionLoader;

/**
 * The factory that provides {@link Lemmatizer} default implementation and
 * resources.
 */
public class LemmatizerFactory extends BaseToolFactory {

  /**
   * Instantiates a {@link LemmatizerFactory} that provides the default implementation
   * of the resources.
   */
  public LemmatizerFactory() {
  }

  /**
   * Instantiates a {@link LemmatizerFactory} via a given {@code subclassName}.
   *
   * @param subclassName The class name used for instantiation. If {@code null}, an
   *                     instance of {@link LemmatizerFactory} will be returned
   *                     per default. Otherwise, the {@link ExtensionLoader} mechanism
   *                     is applied to load the requested {@code subclassName}.
   *
   * @return A valid {@link LemmatizerFactory} instance.
   */
  public static LemmatizerFactory create(String subclassName)
      throws InvalidFormatException {
    if (subclassName == null) {
      // will create the default factory
      return new LemmatizerFactory();
    }
    try {
      return ExtensionLoader.instantiateExtension(LemmatizerFactory.class, subclassName);
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

  /**
   * @return Retrieves a new {@link SequenceValidator} instance.
   */
  public SequenceValidator<String> getSequenceValidator() {
    return new DefaultLemmatizerSequenceValidator();
  }

  /**
   * @return Retrieves a new {@link LemmatizerContextGenerator} instance.
   */
  public LemmatizerContextGenerator getContextGenerator() {
    return new DefaultLemmatizerContextGenerator();
  }
}
