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

package opennlp.uima.namefind;

import java.io.IOException;
import java.io.InputStream;

import org.apache.uima.cas.CAS;
import org.apache.uima.resource.ResourceAccessException;
import org.apache.uima.resource.ResourceInitializationException;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.Span;
import opennlp.uima.dictionary.DictionaryResource;
import opennlp.uima.util.AnnotatorUtil;
import opennlp.uima.util.ExceptionMessages;
import opennlp.uima.util.UimaUtil;

public class DictionaryNameFinder extends AbstractNameFinder {

  private opennlp.tools.namefind.TokenNameFinder mNameFinder;

  /**
   * Initializes a new instance.
   * <p>
   * Note: Use {@link #initialize() } to initialize
   * this instance. Not use the constructor.
   */
  public DictionaryNameFinder() {
    super("OpenNLP Dictionary Name annotator");
  }

  /**
   * Initializes the current instance with the given context.
   * <p>
   * Note: Do all initialization in this method, do not use the constructor.
   */
  public void initialize() throws ResourceInitializationException {

    Dictionary nameFinderDictionary;

    try {
      DictionaryResource modelResource = (DictionaryResource) context
          .getResourceObject(UimaUtil.DICTIONARY_PARAMETER);

      nameFinderDictionary = modelResource.getDictionary();
    } catch (ResourceAccessException e) {

      try {
        String modelName = AnnotatorUtil.getRequiredStringParameter(context,
            UimaUtil.DICTIONARY_PARAMETER);

        InputStream inModel = AnnotatorUtil.getResourceAsStream(context,
            modelName);

        nameFinderDictionary = new Dictionary(inModel);

      } catch (IOException ie) {
        throw new ResourceInitializationException(
            ExceptionMessages.MESSAGE_CATALOG,
            ExceptionMessages.IO_ERROR_DICTIONARY_READING,
            new Object[] {ie.getMessage()});
      }

    }

    mNameFinder = new opennlp.tools.namefind.DictionaryNameFinder(
        nameFinderDictionary);
  }

  protected Span[] find(CAS cas, String[] tokens) {
    return mNameFinder.find(tokens);
  }

  /**
   * Releases allocated resources.
   */
  public void destroy() {
    mNameFinder = null;
  }
}