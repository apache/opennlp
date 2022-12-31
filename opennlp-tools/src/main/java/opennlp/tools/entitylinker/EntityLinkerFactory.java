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

package opennlp.tools.entitylinker;

import java.io.IOException;
import java.util.Objects;

import opennlp.tools.util.ext.ExtensionLoader;

/**
 * Generates a {@link EntityLinker} instances via a {@code properties} file configuration.
 * <p>
 * In the properties file, the linker implementation must be
 * provided using {@code "linker"} as the properties key, and the
 * full class name as value.
 */
public class EntityLinkerFactory {

  /**
   * Retrieves a {@link EntityLinker} instance matching the {@code properties} configuration.
   *
   * @param entityType The type of entity being linked to. This value is used to
   *                   retrieve the implementation of the {@link EntityLinker} from the
   *                   {@link EntityLinker} properties file.
   *                   Must not be {@code null}.
   * @param properties An object that extends {@link EntityLinkerProperties}.
   *                   This object will be passed into the
   *                   {@link EntityLinker#init(EntityLinkerProperties)} method,
   *                   so it is an appropriate place to put additional resources.
   *                   Must not be {@code null}.
   *
   * @return The {@link EntityLinker} instance for the {@code properties} configuration.
   * @throws IOException Thrown if IO errors occurred.
   * @throws IllegalArgumentException Thrown if parameters were invalid.
   */
  public static synchronized EntityLinker<?> getLinker(String entityType, EntityLinkerProperties properties)
      throws IOException {
    if (entityType == null || properties == null) {
      throw new IllegalArgumentException("Null argument in entityLinkerFactory");
    }

    String linkerImplFullName = properties.getProperty("linker." + entityType, "");

    if (linkerImplFullName == null || linkerImplFullName.equals("")) {
      throw new IllegalArgumentException("linker." + entityType + "  property must be set!");
    }

    EntityLinker<?> linker = ExtensionLoader.instantiateExtension(EntityLinker.class, linkerImplFullName);
    linker.init(properties);
    return linker;
  }

  /**
   * Retrieves a {@link EntityLinker} instance matching the {@code properties} configuration.
   *
   * @param properties An object that extends {@link EntityLinkerProperties}.
   *                   This object will be passed into the
   *                   {@link EntityLinker#init(EntityLinkerProperties)} method,
   *                   so it is an appropriate place to put additional resources.
   *                   Must not be {@code null}.
   *
   * @return The {@link EntityLinker} instance for the {@code properties} configuration.
   * @throws IOException Thrown if IO errors occurred.
   * @throws IllegalArgumentException Thrown if parameters were invalid.
   */
  public static synchronized EntityLinker<?> getLinker(EntityLinkerProperties properties) throws IOException {
    Objects.requireNonNull(properties, "properties argument must not be null");

    String linkerImplFullName = properties.getProperty("linker", "");

    if (linkerImplFullName == null || linkerImplFullName.equals("")) {
      throw new IllegalArgumentException("\"linker\" property must be set!");
    }

    EntityLinker<?> linker = ExtensionLoader.instantiateExtension(EntityLinker.class, linkerImplFullName);
    linker.init(properties);
    return linker;
  }
}
