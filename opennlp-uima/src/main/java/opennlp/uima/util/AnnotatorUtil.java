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
   * @param typeSystem The {@link TypeSystem} to use.
   * @param name The name of the type to retrieve.
   *
   * @return The {@link Type} for the {@code name}.
   * @throws OpenNlpAnnotatorProcessException Thrown if no {@link Type} could be found.
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
   * Checks if a {@link Feature} has the expected type, otherwise
   * an exception is thrown.
   *
   * @param feature The {@link Feature} to check for.
   * @param expectedType The type that is expected.
   *                     
   * @throws OpenNlpAnnotatorProcessException Thrown if no {@link Type} did match.
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


  /**
   * Retrieves a {@link Feature} for a specified type and {@code featureName},
   * otherwise an exception is thrown.
   *
   * @param type The {@link Type} to use.
   * @param featureName The name of the feature to retrieve.
   *
   * @return The {@link Feature} if found.
   * @throws OpenNlpAnnotatorProcessException Thrown if no {@link Feature} did match.
   */
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
   * Retrieves a {@link Feature} of the given type.
   *
   * @param type        The {@link Type} to use.
   * @param featureName The name of the feature to retrieve.
   * @param rangeType   The expected range type.
   *                    
   * @return The {@link Feature} if found.
   * @throws OpenNlpAnnotatorProcessException Thrown if no {@link Feature} did match.
   */
  public static Feature getRequiredFeature(Type type, String featureName,
                                           String rangeType)
      throws AnalysisEngineProcessException {

    Feature feature = getRequiredFeature(type, featureName);

    checkFeatureType(feature, rangeType);

    return feature;
  }

  /**
   * Retrieves a {@link Feature feature parameter} of specified type.
   *
   * @param context The {@link UimaContext} to use.
   * @param type The {@link Type} of the {@link Feature} to get.
   * @param featureNameParameter The name of the feature parameter.
   *
   * @return The {@link Feature} if found.
   * @throws OpenNlpAnnotatorProcessException Thrown if no {@link Feature} did match.
   */
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

  /**
   * Retrieves a {@link Feature feature parameter} of specified type.
   *
   * @param context The {@link UimaContext} to use.
   * @param type The {@link Type} of the {@link Feature} to get.
   * @param featureNameParameter The name of the feature parameter.
   * @param rangeTypeName The name of expected range type.
   *
   * @return The {@link Feature} if found.
   * @throws OpenNlpAnnotatorProcessException Thrown if no {@link Feature} did match.
   */
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

  /**
   * Retrieves a type of the given name from the given type system.
   *
   * @param context The {@link UimaContext} to use.
   * @param typeSystem The {@link TypeSystem} to use.
   * @param parameter The name of the type to retrieve.
   *
   * @return The {@link Type} for the {@code name}.
   * @throws OpenNlpAnnotatorProcessException Thrown if no {@link Type} could be found.
   */
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
   * @param context The {@link UimaContext} to use.
   * @param parameter The name of the type to retrieve.
   *
   * @return The {@link String} value retrieved for a specified {@code parameter}
   *         from the {@code context}.
   */
  public static String getRequiredStringParameter(UimaContext context, String parameter)
      throws ResourceInitializationException {

    String value = getOptionalStringParameter(context, parameter);

    checkForNull(value, parameter);

    return value;
  }

  /**
   * Retrieves a required parameter from the given context.
   *
   * @param context The {@link UimaContext} to use.
   * @param parameter The name of the type to retrieve.
   *
   * @return The {@link Integer} value retrieved for a specified {@code parameter}
   *         from the {@code context}.
   * @throws ResourceInitializationException Thrown if no value} could be found.
   */
  public static Integer getRequiredIntegerParameter(UimaContext context, String parameter)
      throws ResourceInitializationException {

    Integer value = getOptionalIntegerParameter(context, parameter);

    checkForNull(value, parameter);

    return value;
  }

  /**
   * Retrieves a required parameter from the given context.
   *
   * @param context The {@link UimaContext} to use.
   * @param parameter The name of the type to retrieve.
   *
   * @return The {@link Float} value retrieved for a specified {@code parameter}
   *         from the {@code context}.
   * @throws ResourceInitializationException Thrown if no value} could be found.
   */
  public static Float getRequiredFloatParameter(UimaContext context, String parameter)
      throws ResourceInitializationException {

    Float value = getOptionalFloatParameter(context, parameter);

    checkForNull(value, parameter);

    return value;
  }

  /**
   * Retrieves a required parameter from the given context.
   *
   * @param context The {@link UimaContext} to use.
   * @param parameter The name of the type to retrieve.
   *
   * @return The {@link Boolean} value retrieved for a specified {@code parameter}
   *         from the {@code context}.
   * @throws ResourceInitializationException Thrown if no value} could be found.
   */
  public static Boolean getRequiredBooleanParameter(UimaContext context, String parameter)
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


  /**
   * Retrieves an optional {@link Feature feature parameter} of specified type.
   *
   * @param context The {@link UimaContext} to use.
   * @param nameType The {@link Type} of the {@link Feature} to get.
   * @param featureNameParameter The name of the feature parameter.
   * @param rangeTypeName The name of expected range type.
   *
   * @return The {@link Feature} if found.
   * @throws OpenNlpAnnotatorProcessException Thrown if no {@link Feature} did match.
   */
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

  /**
   * Retrieves an optional {@link Feature feature parameter} of specified type.
   *
   * @param type The {@link Type} of the {@link Feature} to get.
   * @param featureName The name of the feature parameter.
   * @param rangeType The expected range type.
   *
   * @return The {@link Feature} if found.
   * @throws OpenNlpAnnotatorProcessException Thrown if no {@link Feature} did match.
   */
  public static Feature getOptionalFeature(Type type, String featureName, String rangeType)
      throws AnalysisEngineProcessException {

    Feature feature = type.getFeatureByBaseName(featureName);

    checkFeatureType(feature, rangeType);

    return feature;
  }

  /**
   * Retrieves an optional {@link Feature feature parameter} of specified type.
   *
   * @param context The {@link UimaContext} to use.
   * @param typeSystem The {@link TypeSystem} to use.
   * @param parameter The name of the type to retrieve.
   *
   * @return The {@link Type} for the {@code name}.
   * @throws OpenNlpAnnotatorProcessException Thrown if no {@link Type} could be found.
   */
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
   * Retrieves an optional parameter from the given {@link UimaContext}.
   *
   * @param context The {@link UimaContext} to use.
   * @param parameter The name of the parameter to retrieve.
   *
   * @return The {@link String parameter} or {@code null} if not set.
   * @throws ResourceInitializationException Thrown if the parameter type was not of the expected type.
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

  /**
   * Retrieves an optional parameter array from the given {@link UimaContext}.
   *
   * @param context The {@link UimaContext} to use.
   * @param parameter The name of the parameter to retrieve.
   *
   * @return The {@link String parameter array} or an empty array if not set.
   * @throws ResourceInitializationException Thrown if the parameter type was not of the expected type.
   */
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
          ExceptionMessages.WRONG_PARAMETER_TYPE, new Object[] {parameter, "String array"});
    }
  }

  /**
   * Retrieves an optional parameter from the given {@link UimaContext}.
   *
   * @param context The {@link UimaContext} to use.
   * @param parameter The name of the parameter to retrieve.
   *
   * @return The {@link Integer parameter} or {@code null} if not set.
   * @throws ResourceInitializationException Thrown if the parameter type was not of the expected type.
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
   * Retrieves an optional parameter from the given {@link UimaContext}.
   *
   * @param context The {@link UimaContext} to use.
   * @param parameter The name of the parameter to retrieve.
   *
   * @return The {@link Float parameter} or {@code null} if not set.
   * @throws ResourceInitializationException Thrown if the parameter type was not of the expected type.
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
   * Retrieves an optional parameter from the given {@link UimaContext}.
   *
   * @param context The {@link UimaContext} to use.
   * @param parameter The name of the parameter to retrieve.
   *
   * @return The {@link Boolean parameter} or {@code null} if not set.
   * @throws ResourceInitializationException Thrown if the parameter type was not of the expected type.
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

  /**
   * Retrieves an optional parameter from the given {@link UimaContext}.
   *
   * @param context The {@link UimaContext} to use.
   * @param parameter The name of the parameter to retrieve.
   *
   * @return The {@link Object parameter} or {@code null} if not set.
   * @throws ResourceInitializationException Thrown if the parameter type was not of the expected type.
   */
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
   * Opens an {@link InputStream} for a resource via the given {@link UimaContext}.
   *
   * @param context The {@link UimaContext} to use.
   * @param name The name that identifies the resource.
   * @return A valid, open {@link InputStream}.
   * @throws ResourceInitializationException Thrown if the resource could not be found.
   */
  public static InputStream getResourceAsStream(UimaContext context, String name)
      throws ResourceInitializationException {

    final InputStream inResource = getOptionalResourceAsStream(context, name);

    if (inResource == null) {
      throw new ResourceInitializationException(
          ExceptionMessages.MESSAGE_CATALOG,
          ExceptionMessages.IO_ERROR_MODEL_READING, new Object[] {name + " could not be found!"});
    }

    return inResource;
  }

  /**
   * Opens an {@link InputStream} for an optional resource via the given {@link UimaContext}.
   *
   * @param context The {@link UimaContext} to use.
   * @param name The name that identifies the resource.
   * @return A valid, open {@link InputStream}.
   * @throws ResourceInitializationException Thrown if the resource could not be found.
   */
  public static InputStream getOptionalResourceAsStream(UimaContext context,
                                                        String name)
      throws ResourceInitializationException {

    final InputStream inResource;

    try {
      inResource = context.getResourceAsStream(name);
    } catch (ResourceAccessException e) {
      throw new ResourceInitializationException(e);
    }

    return inResource;
  }

  /**
   * Creates a {@link Dictionary} via the given {@link UimaContext}.
   *
   * @param context The {@link UimaContext} to use.
   * @param dictionaryParameter The name that identifies the dictionary.
   *                            
   * @return A valid {@link Dictionary} or {@code null} if IO errors occurred.
   * @throws ResourceInitializationException Thrown if the resource could not be found.
   */
  public static Dictionary createOptionalDictionary(UimaContext context,
                                                    String dictionaryParameter)
      throws ResourceInitializationException {

    String dictionaryName = AnnotatorUtil.getOptionalStringParameter(context,
        dictionaryParameter);

    Dictionary dictionary = null;

    if (dictionaryName != null) {

      Logger logger = context.getLogger();

      try (InputStream dictIn = AnnotatorUtil.getOptionalResourceAsStream(context,
              dictionaryName)) {

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
