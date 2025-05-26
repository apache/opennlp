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

package opennlp.tools.models;

import java.io.IOException;

import opennlp.tools.models.simple.SimpleClassPathModelFinder;
import opennlp.tools.util.model.BaseModel;

/**
 * A default implementation of {@link ClassPathModelProvider} which by default relies on
 * {@link SimpleClassPathModelFinder} to scan for models in the current classpath.
 *
 * @see ClassPathModelFinder
 * @see ClassPathModelLoader
 */
public class DefaultClassPathModelProvider implements ClassPathModelProvider {

  private final ClassPathModelFinder finder;
  private final ClassPathModelLoader loader;

  /**
   * Instantiates a {@link DefaultClassPathModelProvider} with a {@link SimpleClassPathModelFinder}
   * and uses a default {@link ClassPathModelLoader}.
   * By default, it scans for the {@link ClassPathModelFinder#OPENNLP_MODEL_JAR_PREFIX} pattern for
   * available model jars.
   *
   * @implNote If you require a different model jar prefix pattern for your environment and or
   *           model file naming strategy, please use
   *           {@link #DefaultClassPathModelProvider(ClassPathModelFinder, ClassPathModelLoader)
   *           constructor} with two arguments.
   */
  public DefaultClassPathModelProvider() {
    this(new SimpleClassPathModelFinder(), new ClassPathModelLoader());
  }

  /**
   * Instantiates a {@link DefaultClassPathModelProvider} with the specified {@code modelJarPattern},
   * a {@link ClassPathModelFinder model finder} and a {@link ClassPathModelLoader model loader} instance.
   *
   * @param finder          The {@link ClassPathModelFinder} instance to use for this provider.
   *                        It must not be {@code null}.
   * @param loader          The {@link ClassPathModelLoader} instance to use for this provider.
   *                        It must not be {@code null}.
   * @throws IllegalArgumentException Thrown if one of the specified parameters were invalid.
   */
  public DefaultClassPathModelProvider(ClassPathModelFinder finder, ClassPathModelLoader loader) {
    if (finder == null) {
      throw new IllegalArgumentException("ClassPathModelFinder cannot be null.");
    }
    if (loader == null) {
      throw new IllegalArgumentException("ClassPathModelLoader cannot be null.");
    }
    this.finder = finder;
    this.loader = loader;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T extends BaseModel> T load(String lang, ModelType type, Class<T> modelType)
          throws IOException {
    return load(lang, type, modelType, false);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T extends BaseModel> T load(String lang, ModelType type, Class<T> modelType,
                                      boolean reloadCache) throws IOException {
    return loader.load(finder.findModels(reloadCache), lang, type, modelType);
  }
}
