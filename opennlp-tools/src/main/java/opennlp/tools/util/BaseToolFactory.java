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

package opennlp.tools.util;

import java.util.HashMap;
import java.util.Map;

import opennlp.tools.util.ext.ExtensionLoader;
import opennlp.tools.util.model.ArtifactProvider;
import opennlp.tools.util.model.ArtifactSerializer;

/**
 * Base class for all tool {@code factories}.
 * <p>
 * Extensions of this class should:
 * <ul>
 *  <li>implement an empty constructor,</li>
 *  <li>implement a constructor that takes the {@link ArtifactProvider}},</li>
 *  <li>override {@link #createArtifactMap()} and
 *      {@link #createArtifactSerializersMap()} methods if necessary.</li>
 * </ul>
 */
public abstract class BaseToolFactory {

  protected ArtifactProvider artifactProvider;

  /**
   * All subclasses should have an empty constructor
   */
  public BaseToolFactory() {
  }

  /**
   * Initializes the ToolFactory with an artifact provider.
   */
  protected void init(ArtifactProvider artifactProvider) {
    this.artifactProvider = artifactProvider;
  }

  /**
   * Creates a {@link Map} with pairs of keys and {@link ArtifactSerializer}.
   * The models implementation should call this method from
   * {@code BaseModel#createArtifactSerializersMap}.
   * <p>
   * The base implementation will return a {@link HashMap} that should be
   * populated by subclasses.
   */
  @SuppressWarnings("rawtypes")
  public Map<String, ArtifactSerializer> createArtifactSerializersMap() {
    return new HashMap<>();
  }

  /**
   * A model's implementation should call this constructor that creates a model
   * programmatically.
   * <p>
   * The base implementation will return a {@link HashMap} that should be
   * populated by subclasses.
   *
   * @return Retrieves a {@link Map} with pairs of keys and objects.
   */
  public Map<String, Object> createArtifactMap() {
    return new HashMap<>();
  }

  /**
   * @return Retrieves the manifest entries to be added to the model manifest.
   */
  public Map<String, String> createManifestEntries() {
    return new HashMap<>();
  }

  /**
   * Validates the parsed artifacts.
   * <p>
   * Note:
   * Subclasses should generally invoke {@code super.validateArtifactMap} at the beginning
   * of this method.
   *
   * @throws InvalidFormatException Thrown if validation found invalid states.
   */
  public abstract void validateArtifactMap() throws InvalidFormatException;

  /**
   * Instantiates a {@link BaseToolFactory} via a given {@code subclassName}.
   *
   * @param subclassName The class name used for instantiation. The {@link ExtensionLoader}
   *                     mechanism is applied to load the requested {@code subclassName}.
   * @param artifactProvider The {@link ArtifactProvider} to be used.
   *
   * @return A valid {@link BaseToolFactory} instance.
   * @throws InvalidFormatException Thrown if the {@link ExtensionLoader} mechanism failed to
   *                                create the factory associated with {@code subclassName}.
   */
  public static BaseToolFactory create(String subclassName, ArtifactProvider artifactProvider)
          throws InvalidFormatException {

    BaseToolFactory theFactory;
    try {
      // Init the ToolFactory using the ExtensionLoader
      theFactory = ExtensionLoader.instantiateExtension(BaseToolFactory.class, subclassName);

      if (theFactory != null) {
        theFactory.init(artifactProvider);
      }
    } catch (Exception e) {
      String msg = "Could not instantiate the " + subclassName
          + ". The initialization threw an exception.";
      throw new InvalidFormatException(msg, e);
    }
    return theFactory;
  }

  /**
   * Instantiates a {@link BaseToolFactory} via a given {@code subclassName}.
   *
   * @param factoryClass The class used for instantiation. The no-arg constructor
   *                     of that class will be used to create and init the resulting object.
   * @param artifactProvider The {@link ArtifactProvider} to be used.
   *
   * @return A valid {@link BaseToolFactory} instance.
   * @throws InvalidFormatException Thrown if the {@link ExtensionLoader} mechanism failed to
   *                                create the factory associated with {@code subclassName}.
   */
  public static BaseToolFactory create(Class<? extends BaseToolFactory> factoryClass,
      ArtifactProvider artifactProvider) throws InvalidFormatException {
    BaseToolFactory theFactory = null;
    if (factoryClass != null) {
      try {
        theFactory = factoryClass.getDeclaredConstructor().newInstance();
        theFactory.init(artifactProvider);
      } catch (Exception e) {
        String msg = "Could not instantiate the " + factoryClass.getCanonicalName()
            + ". The initialization threw an exception.";
        throw new InvalidFormatException(msg, e);
      }
    }
    return theFactory;
  }
}
