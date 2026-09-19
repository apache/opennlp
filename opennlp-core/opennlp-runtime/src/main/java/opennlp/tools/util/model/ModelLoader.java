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

package opennlp.tools.util.model;

import java.io.IOException;
import java.io.InputStream;

import opennlp.tools.util.ext.ExtensionRegistry;

/**
 * Creates a {@link BaseModel} of one type from an {@link InputStream}.
 * <p>
 * Every built-in model type registers a loader in the
 * {@link ExtensionRegistry#getDefault() default registry}, so callers that only
 * hold the model {@link Class} can create it through {@link #forType(Class)}
 * without reflection. A custom model type is registered the same way:
 * {@code registry.register(MyModel.class, ModelLoader.class, (ModelLoader<MyModel>) MyModel::new)}.
 *
 * @param <T> The model type.
 */
@FunctionalInterface
public interface ModelLoader<T extends BaseModel> {

  /**
   * Reads a model from {@code in}. The stream is not closed.
   *
   * @param in The stream to read from. Must not be {@code null}.
   * @return The model.
   *
   * @throws IOException Thrown if the model cannot be read.
   */
  T load(InputStream in) throws IOException;

  /**
   * Looks up the loader registered for {@code type}.
   *
   * @param type The model class. Must not be {@code null}.
   * @param <T> The model type.
   * @return The registered loader.
   *
   * @throws IllegalArgumentException Thrown if {@code type} is {@code null} or
   *         no loader is registered for it.
   */
  @SuppressWarnings("unchecked")
  static <T extends BaseModel> ModelLoader<T> forType(Class<T> type) {
    if (type == null) {
      throw new IllegalArgumentException("type must not be null");
    }
    final ModelLoader<?> loader = ExtensionRegistry.getDefault().factory(type.getName(), ModelLoader.class);
    if (loader == null) {
      throw new IllegalArgumentException("No ModelLoader is registered for " + type.getName()
          + ". Register one through an ExtensionRegistrar.");
    }
    return in -> type.cast(loader.load(in));
  }
}
