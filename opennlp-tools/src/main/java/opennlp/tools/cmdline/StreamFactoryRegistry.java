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

import java.util.HashMap;
import java.util.Map;

import opennlp.tools.formats.BioNLP2004NameSampleStreamFactory;
import opennlp.tools.formats.ChunkerSampleStreamFactory;
import opennlp.tools.formats.Conll02NameSampleStreamFactory;
import opennlp.tools.formats.Conll03NameSampleStreamFactory;
import opennlp.tools.formats.ConllXPOSSampleStreamFactory;
import opennlp.tools.formats.ConllXSentenceSampleStreamFactory;
import opennlp.tools.formats.ConllXTokenSampleStreamFactory;
import opennlp.tools.formats.DocumentSampleStreamFactory;
import opennlp.tools.formats.EvalitaNameSampleStreamFactory;
import opennlp.tools.formats.LeipzigDocumentSampleStreamFactory;
import opennlp.tools.formats.LemmatizerSampleStreamFactory;
import opennlp.tools.formats.NameSampleDataStreamFactory;
import opennlp.tools.formats.ParseSampleStreamFactory;
import opennlp.tools.formats.SentenceSampleStreamFactory;
import opennlp.tools.formats.TokenSampleStreamFactory;
import opennlp.tools.formats.WordTagSampleStreamFactory;
import opennlp.tools.formats.ad.ADChunkSampleStreamFactory;
import opennlp.tools.formats.ad.ADNameSampleStreamFactory;
import opennlp.tools.formats.ad.ADPOSSampleStreamFactory;
import opennlp.tools.formats.ad.ADSentenceSampleStreamFactory;
import opennlp.tools.formats.ad.ADTokenSampleStreamFactory;
import opennlp.tools.formats.brat.BratNameSampleStreamFactory;
import opennlp.tools.formats.convert.NameToSentenceSampleStreamFactory;
import opennlp.tools.formats.convert.NameToTokenSampleStreamFactory;
import opennlp.tools.formats.convert.POSToSentenceSampleStreamFactory;
import opennlp.tools.formats.convert.POSToTokenSampleStreamFactory;
import opennlp.tools.formats.convert.ParseToPOSSampleStreamFactory;
import opennlp.tools.formats.convert.ParseToSentenceSampleStreamFactory;
import opennlp.tools.formats.convert.ParseToTokenSampleStreamFactory;
import opennlp.tools.formats.frenchtreebank.ConstitParseSampleStreamFactory;
import opennlp.tools.formats.letsmt.LetsmtSentenceStreamFactory;
import opennlp.tools.formats.moses.MosesSentenceSampleStreamFactory;
import opennlp.tools.formats.muc.Muc6NameSampleStreamFactory;
import opennlp.tools.formats.ontonotes.OntoNotesNameSampleStreamFactory;
import opennlp.tools.formats.ontonotes.OntoNotesPOSSampleStreamFactory;
import opennlp.tools.formats.ontonotes.OntoNotesParseSampleStreamFactory;

/**
 * Registry for object stream factories.
 */
public final class StreamFactoryRegistry {

  private static final Map<Class, Map<String, ObjectStreamFactory>> registry = new HashMap<>();

  static {
    ChunkerSampleStreamFactory.registerFactory();
    DocumentSampleStreamFactory.registerFactory();
    NameSampleDataStreamFactory.registerFactory();
    ParseSampleStreamFactory.registerFactory();
    SentenceSampleStreamFactory.registerFactory();
    TokenSampleStreamFactory.registerFactory();
    WordTagSampleStreamFactory.registerFactory();
    LemmatizerSampleStreamFactory.registerFactory();

    NameToSentenceSampleStreamFactory.registerFactory();
    NameToTokenSampleStreamFactory.registerFactory();

    POSToSentenceSampleStreamFactory.registerFactory();
    POSToTokenSampleStreamFactory.registerFactory();

    ParseToPOSSampleStreamFactory.registerFactory();
    ParseToSentenceSampleStreamFactory.registerFactory();
    ParseToTokenSampleStreamFactory.registerFactory();

    OntoNotesNameSampleStreamFactory.registerFactory();
    OntoNotesParseSampleStreamFactory.registerFactory();
    OntoNotesPOSSampleStreamFactory.registerFactory();

    BioNLP2004NameSampleStreamFactory.registerFactory();
    Conll02NameSampleStreamFactory.registerFactory();
    Conll03NameSampleStreamFactory.registerFactory();
    EvalitaNameSampleStreamFactory.registerFactory();
    ConllXPOSSampleStreamFactory.registerFactory();
    ConllXSentenceSampleStreamFactory.registerFactory();
    ConllXTokenSampleStreamFactory.registerFactory();
    LeipzigDocumentSampleStreamFactory.registerFactory();
    ADChunkSampleStreamFactory.registerFactory();
    ADNameSampleStreamFactory.registerFactory();
    ADSentenceSampleStreamFactory.registerFactory();
    ADPOSSampleStreamFactory.registerFactory();
    ADTokenSampleStreamFactory.registerFactory();

    Muc6NameSampleStreamFactory.registerFactory();

    ConstitParseSampleStreamFactory.registerFactory();

    BratNameSampleStreamFactory.registerFactory();

    LetsmtSentenceStreamFactory.registerFactory();
    MosesSentenceSampleStreamFactory.registerFactory();
  }

  public static final String DEFAULT_FORMAT = "opennlp";

  private StreamFactoryRegistry() {
    // not intended to be instantiated
  }

  /**
   * Registers <code>factory</code> which reads format named <code>formatName</code> and
   * instantiates streams producing objects of <code>sampleClass</code> class.
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
      formats = new HashMap<>();
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
   * Unregisters a factory which reads format named <code>formatName</code> and
   * instantiates streams producing objects of <code>sampleClass</code> class.
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
   * Returns all factories which produce objects of <code>sampleClass</code> class.
   *
   * @param sampleClass class of the objects, produced by the streams instantiated by the factory
   * @return formats mapped to factories
   */
  @SuppressWarnings("unchecked")
  public static <T> Map<String, ObjectStreamFactory<T>> getFactories(Class<T> sampleClass) {
    return (Map<String, ObjectStreamFactory<T>>) (Object) registry.get(sampleClass);
  }

  /**
   * Returns a factory which reads format named <code>formatName</code> and
   * instantiates streams producing objects of <code>sampleClass</code> class.
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
        } catch (InstantiationException | IllegalAccessException e) {
          return null;
        }

      } catch (ClassNotFoundException e) {
        return null;
      }
    }
  }
}
