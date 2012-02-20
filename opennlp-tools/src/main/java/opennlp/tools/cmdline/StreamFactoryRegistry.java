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

package opennlp.tools.cmdline;

import opennlp.tools.formats.*;
import opennlp.tools.formats.ad.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for object stream factories.
 */
public final class StreamFactoryRegistry {

  private static final Map<Class, Map<String, ObjectStreamFactory>> registry =
      new HashMap<Class, Map<String, ObjectStreamFactory>>();

  static {
    ChunkerSampleStreamFactory.registerFactory();
    DocumentSampleStreamFactory.registerFactory();
    NameSampleDataStreamFactory.registerFactory();
    ParseSampleStreamFactory.registerFactory();
    SentenceSampleStreamFactory.registerFactory();
    TokenSampleStreamFactory.registerFactory();
    WordTagSampleStreamFactory.registerFactory();

    NameToSentenceSampleStreamFactory.registerFactory();
    NameToTokenSampleStreamFactory.registerFactory();
    POSToSentenceSampleStreamFactory.registerFactory();
    POSToTokenSampleStreamFactory.registerFactory();

    BioNLP2004NameSampleStreamFactory.registerFactory();
    Conll02NameSampleStreamFactory.registerFactory();
    Conll03NameSampleStreamFactory.registerFactory();
    ConllXPOSSampleStreamFactory.registerFactory();
    ConllXSentenceSampleStreamFactory.registerFactory();
    ConllXTokenSampleStreamFactory.registerFactory();
    LeipzigDocumentSampleStreamFactory.registerFactory();
    ADChunkSampleStreamFactory.registerFactory();
    ADNameSampleStreamFactory.registerFactory();
    ADSentenceSampleStreamFactory.registerFactory();
    ADPOSSampleStreamFactory.registerFactory();
  }

  public static final String DEFAULT_FORMAT = "opennlp";

  private StreamFactoryRegistry() {
    // not intended to be instantiated
  }

  /**
   * Registers <param>factory</param> which reads format named <param>formatName</param> and
   * instantiates streams producing objects of <param>sampleClass</param> class.
   *
   * @param sampleClass class of the objects, produced by the streams instantiated by the factory
   * @param formatName  name of the format
   * @param factory     instance of the factory
   * @return true if the factory was successfully registered
   */
  public static boolean registerFactory(Class sampleClass,
                                        String formatName,
                                        ObjectStreamFactory factory) {
    boolean result;
    Map<String, ObjectStreamFactory> formats = registry.get(sampleClass);
    if (null == formats) {
      formats = new HashMap<String, ObjectStreamFactory>();
    }
    if (!formats.containsKey(formatName)) {
      formats.put(formatName, factory);
      registry.put(sampleClass, formats);
      result = true;
    } else {
      result = false;
    }
    return result;
  }

  /**
   * Unregisters a factory which reads format named <param>formatName</param> and
   * instantiates streams producing objects of <param>sampleClass</param> class.
   *
   * @param sampleClass class of the objects, produced by the streams instantiated by the factory
   * @param formatName  name of the format
   */
  public static void unregisterFactory(Class sampleClass, String formatName) {
    Map<String, ObjectStreamFactory> formats = registry.get(sampleClass);
    if (null != formats) {
      if (formats.containsKey(formatName)) {
        formats.remove(formatName);
      }
    }
  }

  /**
   * Returns all factories which produce objects of <param>sampleClass</param> class.
   *
   * @param sampleClass class of the objects, produced by the streams instantiated by the factory
   * @return formats mapped to factories
   */
  @SuppressWarnings("unchecked")
  public static <T> Map<String, ObjectStreamFactory<T>> getFactories(Class<T> sampleClass) {
    return (Map<String, ObjectStreamFactory<T>>) (Object) registry.get(sampleClass);
  }

  /**
   * Returns a factory which reads format named <param>formatName</param> and
   * instantiates streams producing objects of <param>sampleClass</param> class.
   *
   * @param sampleClass class of the objects, produced by the streams instantiated by the factory
   * @param formatName  name of the format, if null, assumes OpenNLP format
   * @return factory instance
   */
  @SuppressWarnings("unchecked")
  public static <T> ObjectStreamFactory<T> getFactory(Class<T> sampleClass,
                                                          String formatName) {
    if (null == formatName) {
      formatName = DEFAULT_FORMAT;
    }
    
    ObjectStreamFactory<T> factory = registry.containsKey(sampleClass) ?
        registry.get(sampleClass).get(formatName) : null;
    
    if (factory != null) {
      return factory;
    }
    else {
      try {
        Class<?> factoryClazz = Class.forName(formatName);
        
        // TODO: Need to check if it can produce the desired output
        // Otherwise there will be class cast exceptions later in the flow
        
        try {
          return (ObjectStreamFactory<T>) factoryClazz.newInstance();
        } catch (InstantiationException e) {
        	return null;
        } catch (IllegalAccessException e) {
          return null;
        }
        
      } catch (ClassNotFoundException e) {
        return null;
      }
    }
  }
}