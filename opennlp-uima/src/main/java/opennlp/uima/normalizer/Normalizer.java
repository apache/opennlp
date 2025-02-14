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

package opennlp.uima.normalizer;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.Set;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.CasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import opennlp.tools.util.StringList;
import opennlp.uima.namefind.NameFinder;
import opennlp.uima.util.AnnotatorUtil;
import opennlp.uima.util.ExceptionMessages;
import opennlp.uima.util.UimaUtil;

/**
 * The Normalizer tries the structure annotations. The structured value
 * is then assigned to a field of the annotation.
 * <p>
 * The process depends on the
 * <p>
 * string Tokens must be (fuzzy) mapped to categories eg. a month, a day or a
 * year (use dictionary) integer, float tokens must be parsed eg. for percentage
 * or period boolean tokens must be parsed eg is there any ???
 * <p>
 * <p>
 * Restricted set of outcomes throw an error if not matched or silently fail
 * unrestricted set of outcomes.
 */
public class Normalizer extends CasAnnotator_ImplBase {

  /**
   * This set contains all supported range types.
   */
  private static final Set<String> SUPPORTED_TYPES;

  static {
    SUPPORTED_TYPES = Set.of(CAS.TYPE_NAME_STRING,
            CAS.TYPE_NAME_BYTE, CAS.TYPE_NAME_SHORT,
            CAS.TYPE_NAME_INTEGER, CAS.TYPE_NAME_LONG,
            CAS.TYPE_NAME_FLOAT, CAS.TYPE_NAME_DOUBLE);
  }

  private UimaContext context;

  private Logger mLogger;

  /**
   * The annotation marks the text to structure.
   */
  private Type mNameType;

  /**
   * The target type which the text should have. This type must be primitive.
   * <p>
   * It should not be possible to assign something to this feature with is not
   * structured. The feature should define allowed values.
   */
  private Feature mStructureFeature;

  // private Type mSentenceType;

  private StringDictionary mLookupDictionary;

  /**
   * Initializes a {@link Normalizer} instance.
   *
   * @apiNote Use {@link #initialize(UimaContext)} to initialize this instance.
   * Do not use the constructor.
   */
  private Normalizer() {
    // must not be implemented !
  }

  /**
   * Initializes the current instance with the given {@link UimaContext context}.
   * <p>
   * @param context context to initialize
   * @throws ResourceInitializationException Thrown if errors occurred during initialization of resources.
   *
   * @implNote Do all initialization in this method, do not use the constructor.
   */
  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {

    super.initialize(context);

    this.context = context;

    mLogger = context.getLogger();

    if (mLogger.isLoggable(Level.DEBUG)) {
      mLogger.log(Level.DEBUG, "Initializing the OpenNLP Normalizer annotator.");
    }

    try {
      String modelName = AnnotatorUtil.getOptionalStringParameter(context,
          UimaUtil.DICTIONARY_PARAMETER);

      if (modelName != null) {
        InputStream inModel = AnnotatorUtil.getResourceAsStream(context,
            modelName);

        mLookupDictionary = new StringDictionary(inModel);
      }
    } catch (IOException e) {
      throw new ResourceInitializationException(
          ExceptionMessages.MESSAGE_CATALOG, "io_error_model_reading",
          new Object[] {e.getMessage()}, e);
    }
  }

  /**
   * Initializes the type system.
   * @param typeSystem type system to initialize
   */
  @Override
  public void typeSystemInit(TypeSystem typeSystem)
      throws AnalysisEngineProcessException {

    // sentence type
    // String sentenceTypeName =
    // AnnotatorUtil.getRequiredStringParameter(mContext,
    // UimaUtil.SENTENCE_TYPE_PARAMETER);

    // mSentenceType = AnnotatorUtil.getType(typeSystem, sentenceTypeName);

    // name type
    mNameType = AnnotatorUtil.getRequiredTypeParameter(context, typeSystem,
        NameFinder.NAME_TYPE_PARAMETER);

    mStructureFeature = AnnotatorUtil.getRequiredFeatureParameter(context,
        mNameType, "opennlp.uima.normalizer.StructureFeature");

    if (!SUPPORTED_TYPES.contains(mStructureFeature.getRange().getName())) {
      throw new AnalysisEngineProcessException(
          ExceptionMessages.MESSAGE_CATALOG, "range_type_unsupported",
          new Object[] {mStructureFeature.getRange().getName()});
    }
  }

  @Override
  public void process(CAS tcas) {

    FSIndex<AnnotationFS> sentenceIndex = tcas.getAnnotationIndex(mNameType);

    for (AnnotationFS nameAnnotation : sentenceIndex) {
      // check if the document language is supported
      String language = tcas.getDocumentLanguage();

      if (!NumberUtil.isLanguageSupported(language)) {
        if (mLogger.isLoggable(Level.INFO)) {
          mLogger.log(Level.INFO, "Unsupported language: " + language);
        }
        continue;
      }

      String text = nameAnnotation.getCoveredText();

      // if possible replace text with normalization from dictionary
      if (mLookupDictionary != null) {

        StringList tokens = new StringList(text);

        String normalizedText = mLookupDictionary.get(tokens);

        if (normalizedText != null) {
          text = normalizedText;
        }
      }
      String name = mStructureFeature.getRange().getName();

      if (CAS.TYPE_NAME_STRING.equals(name)) {
        nameAnnotation.setStringValue(mStructureFeature, text);
      } else {

        Number number;
        try {
          number = NumberUtil.parse(text, language);
        } catch (ParseException e) {
          if (mLogger.isLoggable(Level.WARN)) {
            mLogger.log(Level.WARN, "Invalid number format: " + text);
          }
          continue;
        }

        if (CAS.TYPE_NAME_BYTE.equals(name)) {
          nameAnnotation.setByteValue(mStructureFeature, number.byteValue());
        } else if (CAS.TYPE_NAME_SHORT.equals(name)) {
          nameAnnotation.setShortValue(mStructureFeature, number.shortValue());
        } else if (CAS.TYPE_NAME_INTEGER.equals(name)) {
          nameAnnotation.setIntValue(mStructureFeature, number.intValue());
        } else if (CAS.TYPE_NAME_LONG.equals(name)) {
          nameAnnotation.setLongValue(mStructureFeature, number.longValue());
        } else if (CAS.TYPE_NAME_FLOAT.equals(name)) {
          nameAnnotation.setFloatValue(mStructureFeature, number.floatValue());
        } else if (CAS.TYPE_NAME_DOUBLE.equals(name)) {
          nameAnnotation.setDoubleValue(mStructureFeature, number.doubleValue());
        }
      }
    }
  }
}
