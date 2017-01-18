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
import java.util.Collections;
import java.util.HashSet;
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
 * is than assigned to a field of the annotation.
 * <p>
 * The process depends on the
 * <p>
 * string Tokens must be (fuzzy) mapped to categories eg. a month, a day or a
 * year (use dictionary) integer, float tokens must be parsed eg. for percentage
 * or period boolean tokens must be parsed eg is there any ???
 * <p>
 * <p>
 * restricted set of outcomes throw error if not matched or silently fail
 * unrestricted set of outcomes
 */
public class Normalizer extends CasAnnotator_ImplBase {

  /**
   * This set contains all supported range types.
   */
  private static final Set<String> SUPPORTED_TYPES;

  static {
    Set<String> supportedTypes = new HashSet<>();

    supportedTypes.add(CAS.TYPE_NAME_STRING);
    supportedTypes.add(CAS.TYPE_NAME_BYTE);
    supportedTypes.add(CAS.TYPE_NAME_SHORT);
    supportedTypes.add(CAS.TYPE_NAME_INTEGER);
    supportedTypes.add(CAS.TYPE_NAME_LONG);
    supportedTypes.add(CAS.TYPE_NAME_FLOAT);
    supportedTypes.add(CAS.TYPE_NAME_DOUBLE);

    SUPPORTED_TYPES = Collections.unmodifiableSet(supportedTypes);
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
   * Initializes a new instance.
   * <p>
   * Note: Use {@link #initialize(UimaContext) } to initialize this instance. Not
   * use the constructor.
   */
  public Normalizer() {
    // must not be implemented !
  }

  /**
   * Initializes the current instance with the given context.
   * <p>
   * Note: Do all initialization in this method, do not use the constructor.
   */
  public void initialize(UimaContext context)
      throws ResourceInitializationException {

    super.initialize(context);

    this.context = context;

    mLogger = context.getLogger();

    if (mLogger.isLoggable(Level.INFO)) {
      mLogger.log(Level.INFO, "Initializing the OpenNLP Normalizer annotator.");
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
   */
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

      if (CAS.TYPE_NAME_STRING.equals(mStructureFeature.getRange().getName())) {
        nameAnnotation.setStringValue(mStructureFeature, text);
      } else {

        Number number;
        try {
          number = NumberUtil.parse(text, language);
        } catch (ParseException e) {
          if (mLogger.isLoggable(Level.INFO)) {
            mLogger.log(Level.INFO, "Invalid number format: " + text);
          }
          continue;
        }

        if (CAS.TYPE_NAME_BYTE.equals(mStructureFeature.getRange().getName())) {
          nameAnnotation.setByteValue(mStructureFeature, number.byteValue());
        } else if (CAS.TYPE_NAME_SHORT.equals(mStructureFeature.getRange()
            .getName())) {
          nameAnnotation.setShortValue(mStructureFeature, number.shortValue());
        } else if (CAS.TYPE_NAME_INTEGER.equals(mStructureFeature.getRange()
            .getName())) {
          nameAnnotation.setIntValue(mStructureFeature, number.intValue());
        } else if (CAS.TYPE_NAME_LONG.equals(mStructureFeature.getRange()
            .getName())) {
          nameAnnotation.setLongValue(mStructureFeature, number.longValue());
        } else if (CAS.TYPE_NAME_FLOAT.equals(mStructureFeature.getRange()
            .getName())) {
          nameAnnotation.setFloatValue(mStructureFeature, number.floatValue());
        } else if (CAS.TYPE_NAME_DOUBLE.equals(mStructureFeature.getRange()
            .getName())) {
          nameAnnotation
              .setDoubleValue(mStructureFeature, number.doubleValue());
        }
      }
    }
  }
}