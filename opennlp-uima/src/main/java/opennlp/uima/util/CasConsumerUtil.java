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

package opennlp.uima.util;

import java.io.IOException;
import java.io.InputStream;

import opennlp.tools.dictionary.Dictionary;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.annotator.AnnotatorConfigurationException;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.resource.ResourceAccessException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.apache.uima.util.Logger;

/**
 * This is a util class for cas consumer.
 */
public final class CasConsumerUtil {
  
  private CasConsumerUtil(){
    // this is a util class must not be instanciated
  }
   
  public static InputStream getOptionalResourceAsStream(UimaContext context, 
	      String name) throws ResourceInitializationException {
      try {
	      return context.getResourceAsStream(name);
	    } catch (ResourceAccessException e) {
	      throw new ResourceInitializationException(
	          ResourceInitializationException.STANDARD_MESSAGE_CATALOG,
	          new Object[] { "There is an internal error in the UIMA SDK: " + 
	          e.getMessage(),
	          e });
	    }   
  }
  
  /**
   * Retrieves a resource as stream from the given context.
   * 
   * @param context
   * @param name
   * @return the stream
   * 
   * @throws AnnotatorConfigurationException
   */
  public static InputStream getResourceAsStream(UimaContext context, 
      String name) throws ResourceInitializationException {
    
    InputStream inResource = getOptionalResourceAsStream(context, name); 
    
    if (inResource == null) {
      throw new ResourceInitializationException(
          ResourceAccessException.STANDARD_MESSAGE_CATALOG,
          new Object[] { "Unable to load resource!" });
    }

    return inResource;
  }
  
  /**
   * Retrieves a type from the given type system.
   * 
   * @param typeSystem
   * @param name
   * @return the type
   * 
   * @throws ResourceInitializationException
   */
  public static Type getType(TypeSystem typeSystem, String name)
      throws ResourceInitializationException {
    Type type = getOptionalType(typeSystem, name);
    
    if (type == null) {
      throw new ResourceInitializationException(
          ResourceInitializationException.INCOMPATIBLE_RANGE_TYPES,
          new Object[] { "Unable to retrive " + name + " type!" });
    }
    
    return type;
  }
  
  /**
   * Retrieves a type from the given type system.
   * 
   * @param typeSystem
   * @param name
   * @return the type
   * 
   * @throws ResourceInitializationException
   */
  public static Type getOptionalType(TypeSystem typeSystem, String name)
      throws ResourceInitializationException {
    return typeSystem.getType(name);
  }
  /**
   * Retrieves a required parameter form the given context.
   * 
   * @param context
   * @param parameter
   * @return the parameter
   * 
   * @throws ResourceInitializationException
   */
  public static String getRequiredStringParameter(UimaContext context,
      String parameter) throws ResourceInitializationException {

    String value = getOptionalStringParameter(context, parameter);
    
    checkForNull(value, parameter);
    
    return value;
  }

  /**
   * Retrieves a required parameter form the given context.
   * 
   * @param context
   * @param parameter
   * @return the parameter
   * 
   * @throws ResourceInitializationException
   */
  public static Integer getRequiredIntegerParameter(UimaContext context,
      String parameter) throws ResourceInitializationException {

    Integer value = getOptionalIntegerParameter(context, parameter);
    
    checkForNull(value, parameter);

    return value;
  }
  
  /**
   * Retrieves a required parameter form the given context.
   * 
   * @param context
   * @param parameter
   * @return the parameter
   * 
   * @throws ResourceInitializationException
   */
  public static Float getRequiredFloatParameter(UimaContext context,
      String parameter) throws ResourceInitializationException {

    Float value = getOptionalFloatParameter(context, parameter);
    
    checkForNull(value, parameter);

    return value;
  }
  
  /**
   * Retrieves a required boolean parameter from the given context.
   * 
   * @param context
   * @param parameter
   * @return the boolean parameter
   * 
   * @throws ResourceInitializationException
   */
  public static Boolean getRequiredBooleanParameter(UimaContext context, 
      String parameter) throws ResourceInitializationException {
    
    Boolean value = getOptionalBooleanParameter(context, parameter);
    
    checkForNull(value, parameter);

    return value;
  }

  private static void checkForNull(Object value, String parameterName) 
      throws ResourceInitializationException{
    
    if (value == null) {
      throw new ResourceInitializationException(
          ResourceInitializationException.STANDARD_MESSAGE_CATALOG,
          new Object[] { "The " + parameterName + " is a " + 
          "requiered parameter!" });
    }
  }
  
  /**
   * Retrieves an optional boolean parameter from the given context.
   * 
   * @param context
   * @param parameter
   * @return the boolean parameter or null if not set
   * @throws ResourceInitializationException 
   */
  public static String getOptionalStringParameter(UimaContext context,
      String parameter) throws ResourceInitializationException {
    
    Object value = getOptionalParameter(context, parameter);
    
    if (value == null) {
      return null;
    }
    else if (value instanceof String) {
      return (String) value;
    }
    else {
      throw new ResourceInitializationException(
          ResourceInitializationException.STANDARD_MESSAGE_CATALOG,
          new Object[] {"The parameter: "  + parameter + " does not have" +
          "the expected type String"});
    }
  }
  
  public static String[] getOptionalStringArrayParameter(UimaContext context,
	    String parameter) throws ResourceInitializationException {

	Object value = getOptionalParameter(context, parameter);

	if (value instanceof String[]) {
	    return (String[]) value;
	} else if (value == null) {
	    return new String[0];
	} else {
	    throw new ResourceInitializationException(
		    ResourceInitializationException.STANDARD_MESSAGE_CATALOG,
		    new Object[] { "The parameter: " + parameter
			    + " does not have" + "the expected type String array" });
	}
    }
  
  /**
    * Retrieves an optional boolean parameter from the given context.
    * 
    * @param context
    * @param parameter
    * @return the boolean parameter or null if not set
    * @throws ResourceInitializationException
    */
  public static Integer getOptionalIntegerParameter(UimaContext context,
      String parameter) throws ResourceInitializationException {
    
    Object value = getOptionalParameter(context, parameter);
    
    if (value == null) {
      return null;
    }
    else if (value instanceof Integer) {
      return (Integer) value;
    }
    else {
      throw new ResourceInitializationException(
          ResourceInitializationException.STANDARD_MESSAGE_CATALOG,
          new Object[] {"The parameter: "  + parameter + " does not have" +
          "the expected type Integer"});
    }
  }
  
  /**
   * Retrieves an optional boolean parameter from the given context.
   * 
   * @param context
   * @param parameter
   * @param defaultValue value to use if the optional parameter is not set
   * 
   * @return the boolean parameter or null if not set
   * @throws ResourceInitializationException
   */
  public static Integer getOptionalIntegerParameter(UimaContext context, String parameter,
      int defaultValue) throws ResourceInitializationException {
    
    Integer value = getOptionalIntegerParameter(context, parameter);
    
    if (value == null)
      value = defaultValue;
    
    return value;
  }
  
  /**
   * Retrieves an optional boolean parameter from the given context.
   * 
   * @param context
   * @param parameter
   * @return the boolean parameter or null if not set
   * @throws ResourceInitializationException 
   */
  public static Float getOptionalFloatParameter(UimaContext context,
      String parameter) throws ResourceInitializationException {
    
    Object value = getOptionalParameter(context, parameter);
    
    if (value == null) {
      return null;
    }
    else if (value instanceof Float) {
      return (Float) value;
    }
    else {
      throw new ResourceInitializationException(
          ResourceInitializationException.STANDARD_MESSAGE_CATALOG,
          new Object[] {"The parameter: "  + parameter + " does not have" +
          "the expected type Float"});
    }
  }
  
  /**
   * Retrieves an optional boolean parameter from the given context.
   * 
   * @param context
   * @param parameter
   * @return the boolean parameter or null if not set
   * @throws ResourceInitializationException 
   */
  public static Boolean getOptionalBooleanParameter(UimaContext context,
      String parameter) throws ResourceInitializationException {
    
    Object value = getOptionalParameter(context, parameter);
    
    if (value == null) {
      return null;
    }
    else if (value instanceof Boolean) {
      return (Boolean) value;
    }
    else {
      throw new ResourceInitializationException(
          ResourceInitializationException.STANDARD_MESSAGE_CATALOG,
          new Object[] {"The parameter: "  + parameter + " does not have" +
          "the expected type Boolean"});
    }
  }
  
  private static Object getOptionalParameter(UimaContext context, 
      String parameter) {
    
    Object value =  context.getConfigParameterValue(parameter);

    Logger logger = context.getLogger();
    
    if (logger.isLoggable(Level.INFO)) {
      logger.log(Level.INFO, parameter + " = " + 
          (value != null ? value.toString() : "not set"));
    }
    
    return value;
  }
 
  /**
   * Checks if the given feature has the expected type otherwise
   * an exception is thrown.
   * 
   * @param feature
   * @param expectedType
   * 
   * @throws ResourceInitializationException - if type does not match
   */
  public static void checkFeatureType(Feature feature, String expectedType) 
  throws ResourceInitializationException {
    if (!feature.getRange().getName().equals(expectedType)) {
      throw new ResourceInitializationException(
          ResourceInitializationException.STANDARD_MESSAGE_CATALOG,
          new Object[] { "The Feature " + feature.getName() + 
              " must be of type " + expectedType + " !"
          });
    }
  }
  
  public static Dictionary createOptionalDictionary(UimaContext context, String parameter) 
  	throws ResourceInitializationException {
	String dictionaryName = CasConsumerUtil.getOptionalStringParameter(
		context, parameter);
	
	Dictionary dictionary = null;

	if (dictionaryName != null) {

	    Logger logger = context.getLogger();

	    try {

		InputStream dictIn = CasConsumerUtil.getOptionalResourceAsStream(context,
			dictionaryName);

		if (dictIn == null) {
			String message = "The dictionary file " + dictionaryName + 
			" does not exist!";

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
	} else
	    return null;
  }
}