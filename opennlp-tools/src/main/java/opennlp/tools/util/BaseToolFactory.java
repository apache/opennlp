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
 * Base class for all tool factories.
 *
 * Extensions of this class should:
 * <ul>
 *  <li>implement an empty constructor (TODO is it necessary?)
 *  <li>implement a constructor that takes the {@link ArtifactProvider} and
 *      calls {@code BaseToolFactory(Map)}
 *  <li>override {@link #createArtifactMap()} and
 *      {@link #createArtifactSerializersMap()} methods if necessary.
 * </ul>
 */
public abstract class BaseToolFactory {

  protected ArtifactProvider artifactProvider;

  /**
   * All sub-classes should have an empty constructor
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
   * {@code BaseModel#createArtifactSerializersMap}
   * <p>
   * The base implementation will return a {@link HashMap} that should be
   * populated by sub-classes.
   */
  @SuppressWarnings("rawtypes")
  public Map<String, ArtifactSerializer> createArtifactSerializersMap() {
    return new HashMap<>();
  }

  /**
   * Creates a {@link Map} with pairs of keys and objects. The models
   * implementation should call this constructor that creates a model
   * programmatically.
   * <p>
   * The base implementation will return a {@link HashMap} that should be
   * populated by sub-classes.
   */
  public Map<String, Object> createArtifactMap() {
    return new HashMap<>();
  }

  /**
   * Creates the manifest entries that will be added to the model manifest
   *
   * @return the manifest entries to added to the model manifest
   */
  public Map<String, String> createManifestEntries() {
    return new HashMap<>();
  }

  /**
   * Validates the parsed artifacts. If something is not
   * valid subclasses should throw an {@link InvalidFormatException}.
   *
   * Note:
   * Subclasses should generally invoke super.validateArtifactMap at the beginning
   * of this method.
   *
   * @throws InvalidFormatException
   */
  public abstract void validateArtifactMap() throws InvalidFormatException;

  public static BaseToolFactory create(String subclassName,
      ArtifactProvider artifactProvider) throws InvalidFormatException {
    BaseToolFactory theFactory;

    try {
      // load the ToolFactory using the default constructor
      theFactory = ExtensionLoader.instantiateExtension(BaseToolFactory.class, subclassName);

      if (theFactory != null) {
        theFactory.init(artifactProvider);
      }
    } catch (Exception e) {
      String msg = "Could not instantiate the " + subclassName
          + ". The initialization throw an exception.";
      System.err.println(msg);
      e.printStackTrace();
      throw new InvalidFormatException(msg, e);
    }
    return theFactory;
  }

  public static BaseToolFactory create(Class<? extends BaseToolFactory> factoryClass,
      ArtifactProvider artifactProvider) throws InvalidFormatException {
    BaseToolFactory theFactory = null;
    if (factoryClass != null) {
      try {
        theFactory = factoryClass.newInstance();
        theFactory.init(artifactProvider);
      } catch (Exception e) {
        String msg = "Could not instantiate the "
            + factoryClass.getCanonicalName()
            + ". The initialization throw an exception.";
        System.err.println(msg);
        e.printStackTrace();
        throw new InvalidFormatException(msg, e);
      }
    }
    return theFactory;
  }
}
