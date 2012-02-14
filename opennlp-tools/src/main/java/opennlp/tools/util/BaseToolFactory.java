/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import opennlp.tools.util.model.ArtifactProvider;
import opennlp.tools.util.model.ArtifactSerializer;
import opennlp.tools.util.model.BaseModel;

/**
 * Base class for all tool factories.
 * 
 * Extensions of this class should: <li>implement an empty constructor (TODO is
 * it necessary?) <li>implement a constructor that takes the
 * {@link ArtifactProvider} and calls {@link #BaseToolFactory(Map)} <li>override
 * {@link #createArtifactMap()} and {@link #createArtifactSerializersMap()}
 * methods if necessary.
 */
public abstract class BaseToolFactory {

  protected final ArtifactProvider artifactProvider;

  /**
   * All sub-classes should have an empty constructor
   */
  public BaseToolFactory() {
    this.artifactProvider = null;
  }

  /**
   * All sub-classes should have a constructor whith this signature
   */
  public BaseToolFactory(ArtifactProvider artifactProvider) {
    this.artifactProvider = artifactProvider;
  }

  /**
   * Creates a {@link Map} with pairs of keys and {@link ArtifactSerializer}.
   * The models implementation should call this method from
   * {@link BaseModel#createArtifactSerializersMap}
   * <p>
   * The base implementation will return a {@link HashMap} that should be
   * populated by sub-classes.
   */
  @SuppressWarnings("rawtypes")
  public Map<String, ArtifactSerializer> createArtifactSerializersMap() {
    return new HashMap<String, ArtifactSerializer>();
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
    return new HashMap<String, Object>();
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
    BaseToolFactory theFactory = null;
    Class<? extends BaseToolFactory> factoryClass = loadSubclass(subclassName);
    if (factoryClass != null) {
      try {
        Constructor<?> constructor = null;
        constructor = factoryClass.getConstructor(ArtifactProvider.class);
        theFactory = (BaseToolFactory) constructor
            .newInstance(artifactProvider);
      } catch (NoSuchMethodException e) {
        String msg = "Could not instantiate the "
            + subclassName
            + ". The mandatry constructor (ArtifactProvider) is missing.";
        System.err.println(msg);
        throw new IllegalArgumentException(msg);
      } catch (Exception e) {
        String msg = "Could not instantiate the "
            + subclassName
            + ". The constructor (ArtifactProvider) throw an exception.";
        System.err.println(msg);
        e.printStackTrace();
        throw new InvalidFormatException(msg);
      }
    }
    return theFactory;
  }

  public static BaseToolFactory create(Class<? extends BaseToolFactory> factoryClass,
      ArtifactProvider artifactProvider) throws InvalidFormatException {
    BaseToolFactory theFactory = null;
    if (factoryClass != null) {
      try {
        Constructor<?> constructor = null;
        constructor = factoryClass.getConstructor(ArtifactProvider.class);
        theFactory = (BaseToolFactory) constructor
            .newInstance(artifactProvider);
      } catch (NoSuchMethodException e) {
        String msg = "Could not instantiate the "
            + factoryClass.getCanonicalName()
            + ". The mandatry constructor (ArtifactProvider) is missing.";
        System.err.println(msg);
        throw new IllegalArgumentException(msg);
      } catch (Exception e) {
        String msg = "Could not instantiate the "
            + factoryClass.getCanonicalName()
            + ". The constructor (ArtifactProvider) throw an exception.";
        System.err.println(msg);
        e.printStackTrace();
        throw new InvalidFormatException(msg);
      }
    }
    return theFactory;
  }
  
  @SuppressWarnings("unchecked")
  protected
  static Class<? extends BaseToolFactory> loadSubclass(
      String factoryName) throws InvalidFormatException {
    Class<? extends BaseToolFactory> factoryClass = null;
    try {
      factoryClass = (Class<? extends BaseToolFactory>) Class
          .forName(factoryName);
    } catch (ClassNotFoundException e) {
      throw new NoClassDefFoundError(
          "Could not find the factory class in the classpath: " + factoryName);
    } catch (ClassCastException e) {
      throw new InvalidFormatException(
          "The factory class does not extend BaseToolFactory: " + factoryName,
          e);
    }
    return factoryClass;
  }
  
}
