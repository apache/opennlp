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

package opennlp.uima.util;

import java.io.IOException;
import java.io.InputStream;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.resource.ResourceAccessException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

import opennlp.tools.dictionary.Dictionary;

/**
 * This is a utility class for Annotators.
 */
public final class AnnotatorUtil {

  private AnnotatorUtil() {
    // util class not must not instantiated
  }

  /**
   * Retrieves a type of the given name from the given type system.
   *
   * @param typeSystem
   * @param name
   * @return the type
   * @throws AnalysisEngineProcessException
   */
  public static Type getType(TypeSystem typeSystem, String name)
      throws AnalysisEngineProcessException {
    Type type = typeSystem.getType(name);

    if (type == null) {
      throw new OpenNlpAnnotatorProcessException(
          ExceptionMessages.TYPE_NOT_FOUND,
          new Object[] {name});
    }

    return type;
  }

  /**
   * Checks if the given feature has the expected type otherwise
   * an exception is thrown.
   *
   * @param feature
   * @param expectedType
   * @throws AnalysisEngineProcessException - if type does not match
   */
  private static void checkFeatureType(Feature feature, String expectedType)
      throws AnalysisEngineProcessException {
    if (!feature.getRange().getName().equals(expectedType)) {
      throw new OpenNlpAnnotatorProcessException(
          ExceptionMessages.WRONG_FEATURE_TYPE,
          new Object[] {feature.getName(), expectedType
          });
    }
  }

  public static Feature getRequiredFeature(Type type, String featureName)
      throws AnalysisEngineProcessException {

    Feature feature = type.getFeatureByBaseName(featureName);

    if (feature == null) {
      throw new OpenNlpAnnotatorProcessException(
          ExceptionMessages.FEATURE_NOT_FOUND, new Object[] {type.getName(), featureName});
    }

    return feature;
  }

  /**
   * Retrieves a required feature from the given type.
   *
   * @param type        the type
   * @param featureName the name of the feature
   * @param rangeType   the expected range type
   * @return the requested parameter
   * @throws AnalysisEngineProcessException
   */
  public static Feature getRequiredFeature(Type type, String featureName,
                                           String rangeType)
      throws AnalysisEngineProcessException {

    Feature feature = getRequiredFeature(type, featureName);

    checkFeatureType(feature, rangeType);

    return feature;
  }

  public static Feature getRequiredFeatureParameter(UimaContext context, Type type,
                                                    String featureNameParameter)
      throws AnalysisEngineProcessException {

    String featureName;

    try {
      featureName = getRequiredStringParameter(context, featureNameParameter);
    } catch (ResourceInitializationException e) {
      throw new OpenNlpAnnotatorProcessException(e);
    }

    return getRequiredFeature(type, featureName);
  }

  public static Feature getRequiredFeatureParameter(UimaContext context,
                                                    Type type, String featureNameParameter,
                                                    String rangeTypeName)
      throws AnalysisEngineProcessException {

    String featureName;
    try {
      featureName = getRequiredStringParameter(context, featureNameParameter);
    } catch (ResourceInitializationException e) {
      throw new OpenNlpAnnotatorProcessException(e);
    }

    return getRequiredFeature(type, featureName, rangeTypeName);
  }

  public static Type getRequiredTypeParameter(UimaContext context,
                                              TypeSystem typeSystem, String parameter)
      throws AnalysisEngineProcessException {

    String typeName;

    try {
      typeName = getRequiredStringParameter(context, parameter);
    } catch (ResourceInitializationException e) {
      throw new OpenNlpAnnotatorProcessException(e);
    }

    return getType(typeSystem, typeName);
  }

  /**
   * Retrieves a required parameter from the given context.
   *
   * @param context
   * @param parameter
   * @return the requested parameter
   * @throws ResourceInitializationException
   */
  public static String getRequiredStringParameter(UimaContext context,
                                                  String parameter)
      throws ResourceInitializationException {

    String value = getOptionalStringParameter(context, parameter);

    checkForNull(value, parameter);

    return value;
  }

  /**
   * Retrieves a required parameter from the given context.
   *
   * @param context
   * @param parameter
   * @return the requested parameter
   * @throws ResourceInitializationException
   */
  public static Integer getRequiredIntegerParameter(UimaContext context,
                                                    String parameter)
      throws ResourceInitializationException {

    Integer value = getOptionalIntegerParameter(context, parameter);

    checkForNull(value, parameter);

    return value;
  }

  /**
   * Retrieves a required parameter from the given context.
   *
   * @param context
   * @param parameter
   * @return the requested parameter
   * @throws ResourceInitializationException
   */
  public static Float getRequiredFloatParameter(UimaContext context,
                                                String parameter)
      throws ResourceInitializationException {

    Float value = getOptionalFloatParameter(context, parameter);

    checkForNull(value, parameter);

    return value;
  }

  /**
   * Retrieves a required parameter from the given context.
   *
   * @param context
   * @param parameter
   * @return the requested parameter
   * @throws ResourceInitializationException
   */
  public static Boolean getRequiredBooleanParameter(UimaContext context,
                                                    String parameter)
      throws ResourceInitializationException {

    Boolean value = getOptionalBooleanParameter(context, parameter);

    checkForNull(value, parameter);

    return value;
  }

  private static void checkForNull(Object value, String parameterName)
      throws ResourceInitializationException {
    if (value == null) {
      throw new ResourceInitializationException(
          ExceptionMessages.MESSAGE_CATALOG,
          ExceptionMessages.PARAMETER_NOT_FOUND,
          new Object[] {parameterName});
    }
  }


  public static Feature getOptionalFeatureParameter(UimaContext context,
                                                    Type nameType, String featureNameParameter,
                                                    String rangeTypeName)
      throws AnalysisEngineProcessException {

    String featureName;
    try {
      featureName = getOptionalStringParameter(context, featureNameParameter);
    } catch (ResourceInitializationException e) {
      throw new OpenNlpAnnotatorProcessException(e);
    }

    if (featureName != null) {
      return getOptionalFeature(nameType, featureName, rangeTypeName);
    } else {
      return null;
    }
  }

  public static Feature getOptionalFeature(Type type, String featureName, String rangeType)
      throws AnalysisEngineProcessException {

    Feature feature = type.getFeatureByBaseName(featureName);

    checkFeatureType(feature, rangeType);

    return feature;
  }

  public static Type getOptionalTypeParameter(UimaContext context,
                                              TypeSystem typeSystem, String parameter)
      throws AnalysisEngineProcessException {
    String typeName;

    try {
      typeName = getOptionalStringParameter(context, parameter);
    } catch (ResourceInitializationException e) {
      throw new OpenNlpAnnotatorProcessException(e);
    }

    if (typeName != null) {
      return getType(typeSystem, typeName);
    } else {
      return null;
    }
  }

  /**
   * Retrieves an optional parameter from the given context.
   *
   * @param context
   * @param parameter
   * @return the parameter or null if not set
   * @throws ResourceInitializationException
   */
  public static String getOptionalStringParameter(UimaContext context,
                                                  String parameter)
      throws ResourceInitializationException {
    Object value = getOptionalParameter(context, parameter);

    if (value instanceof String) {
      return (String) value;
    } else if (value == null) {
      return null;
    } else {
      throw new ResourceInitializationException(
          ExceptionMessages.MESSAGE_CATALOG,
          ExceptionMessages.WRONG_PARAMETER_TYPE,
          new Object[] {parameter, "String"});
    }
  }

  public static String[] getOptionalStringArrayParameter(UimaContext context,
                                                         String parameter)
      throws ResourceInitializationException {
    Object value = getOptionalParameter(context, parameter);

    if (value instanceof String[]) {
      return (String[]) value;
    } else if (value == null) {
      return new String[0];
    } else {
      throw new ResourceInitializationException(
          ExceptionMessages.MESSAGE_CATALOG,
          ExceptionMessages.WRONG_PARAMETER_TYPE, new Object[] {parameter,
          "String array"});
    }
  }

  /**
   * Retrieves an optional parameter from the given context.
   *
   * @param context
   * @param parameter
   * @return the parameter or null if not set
   * @throws ResourceInitializationException
   */
  public static Integer getOptionalIntegerParameter(UimaContext context,
                                                    String parameter)
      throws ResourceInitializationException {

    Object value = getOptionalParameter(context, parameter);

    if (value instanceof Integer) {
      return (Integer) value;
    } else if (value == null) {
      return null;
    } else {
      throw new ResourceInitializationException(
          ExceptionMessages.MESSAGE_CATALOG,
          ExceptionMessages.WRONG_PARAMETER_TYPE,
          new Object[] {parameter, "Integer"});
    }
  }

  /**
   * Retrieves an optional parameter from the given context.
   *
   * @param context
   * @param parameter
   * @return the parameter or null if not set
   * @throws ResourceInitializationException
   */
  public static Float getOptionalFloatParameter(UimaContext context,
                                                String parameter)
      throws ResourceInitializationException {

    Object value = getOptionalParameter(context, parameter);

    if (value instanceof Float) {
      return (Float) value;
    } else if (value == null) {
      return null;
    } else {
      throw new ResourceInitializationException(
          ExceptionMessages.MESSAGE_CATALOG,
          ExceptionMessages.WRONG_PARAMETER_TYPE,
          new Object[] {parameter, "Float"});
    }
  }

  /**
   * Retrieves an optional parameter from the given context.
   *
   * @param context
   * @param parameter
   * @return the parameter or null if not set
   * @throws ResourceInitializationException
   */
  public static Boolean getOptionalBooleanParameter(UimaContext context,
                                                    String parameter)
      throws ResourceInitializationException {
    Object value = getOptionalParameter(context, parameter);

    if (value instanceof Boolean) {
      return (Boolean) value;
    } else if (value == null) {
      return null;
    } else {
      throw new ResourceInitializationException(
          ExceptionMessages.MESSAGE_CATALOG,
          ExceptionMessages.WRONG_PARAMETER_TYPE,
          new Object[] {parameter, "Boolean"});
    }
  }

  private static Object getOptionalParameter(UimaContext context,
                                             String parameter)
      throws ResourceInitializationException {

    Object value = context.getConfigParameterValue(parameter);

    Logger logger = context.getLogger();

    if (logger.isLoggable(Level.INFO)) {
      logger.log(Level.INFO, parameter + " = " +
          (value != null ? value.toString() : "not set"));
    }

    return value;
  }

  /**
   * Retrieves a resource as stream from the given context.
   *
   * @param context
   * @param name
   * @return the stream
   * @throws ResourceInitializationException
   */
  public static InputStream getResourceAsStream(UimaContext context, String name)
      throws ResourceInitializationException {

    InputStream inResource = getOptionalResourceAsStream(context, name);

    if (inResource == null) {
      throw new ResourceInitializationException(
          ExceptionMessages.MESSAGE_CATALOG,
          ExceptionMessages.IO_ERROR_MODEL_READING, new Object[] {name
          + " could not be found!"});
    }

    return inResource;
  }

  public static InputStream getOptionalResourceAsStream(UimaContext context,
                                                        String name)
      throws ResourceInitializationException {

    InputStream inResource;

    try {
      inResource = context.getResourceAsStream(name);
    } catch (ResourceAccessException e) {
      throw new ResourceInitializationException(e);
    }

    return inResource;
  }

  public static Dictionary createOptionalDictionary(UimaContext context,
                                                    String dictionaryParameter)
      throws ResourceInitializationException {

    String dictionaryName = AnnotatorUtil.getOptionalStringParameter(context,
        dictionaryParameter);

    Dictionary dictionary = null;

    if (dictionaryName != null) {

      Logger logger = context.getLogger();

      try {

        InputStream dictIn = AnnotatorUtil.getOptionalResourceAsStream(context,
            dictionaryName);

        if (dictIn == null) {
          String message = "The dictionary file " + dictionaryName
              + " does not exist!";

          if (logger.isLoggable(Level.WARNING)) {
            logger.log(Level.WARNING, message);
          }

          return null;
        }

        dictionary = new Dictionary(dictIn);

      } catch (IOException e) {
        // if this fails just print error message and continue
        String message = "IOException during dictionary reading, "
            + "running without dictionary: " + e.getMessage();

        if (logger.isLoggable(Level.WARNING)) {
          logger.log(Level.WARNING, message);
        }
      }

      return dictionary;
    } else {
      return null;
    }
  }
}
