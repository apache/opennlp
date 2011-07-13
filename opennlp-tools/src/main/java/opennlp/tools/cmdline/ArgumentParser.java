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

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Parser for command line arguments. The parser creates a dynamic proxy which
 * can be access via a command line argument interface.
 * 
 * <p>
 * 
 * The command line argument proxy interface must follow these conventions:<br>
 * - Methods do not define arguments<br>
 * - Method names must start with get<br>
 * - Allowed return types are Integer, Boolean, String, File and Charset.<br>
 * <p>
 * <b>Note:</b> Do not use this class, internal use only!
 */
public class ArgumentParser {

  public @Retention(RetentionPolicy.RUNTIME)
  @interface OptionalParameter {
    public static final String DEFAULT_CHARSET = "DEFAULT_CHARSET";
    public String defaultValue() default "";
  }
  
  public @Retention(RetentionPolicy.RUNTIME)
  @interface ParameterDescription {
    public String valueName();
    public String description() default "";
  }
  
  private interface ArgumentFactory {
    
    static final String INVALID_ARG = "Invalid argument: %s %s \n";
    
    Object parseArgument(Method method, String argName, String argValue);
  }
 
  private static class IntegerArgumentFactory  implements ArgumentFactory {

    public Object parseArgument(Method method, String argName, String argValue) {
      
      Object value = null;
      
      try {
        value = Integer.parseInt(argValue);
      }
      catch (NumberFormatException e) {
        throw new TerminateToolException(-1, String.format(INVALID_ARG, argName, argValue) +
            "Value must be an integer!");
      }
      
      return value;
    }
  }
 
  private static class BooleanArgumentFactory implements ArgumentFactory {

    public Object parseArgument(Method method, String argName, String argValue) {
      return Boolean.parseBoolean(argValue);
    }
  } 
  
  private static class StringArgumentFactory implements ArgumentFactory {
    
    public Object parseArgument(Method method, String argName, String argValue) {
      return argValue;
    }
  } 
  
  private static class FileArgumentFactory implements ArgumentFactory {
    
    public Object parseArgument(Method method, String argName, String argValue) {
      return new File(argValue);
    }
  } 
  
  private static class CharsetArgumentFactory implements ArgumentFactory {
    
    public Object parseArgument(Method method, String argName, String charsetName) {
      
      try {
        if(OptionalParameter.DEFAULT_CHARSET.equals(charsetName)) {
          return Charset.defaultCharset();
        } else if (Charset.isSupported(charsetName)) {
          return Charset.forName(charsetName);
        } else {
          throw new TerminateToolException(-1,  String.format(INVALID_ARG, argName, charsetName) + 
              "Encoding not supported on this platform.");
        }
      } catch (IllegalCharsetNameException e) {
        throw new TerminateToolException(-1, String.format(INVALID_ARG, argName, charsetName) + 
            "Illegal encoding name.");
      }
    }
  } 
  
  private static class ArgumentProxy implements InvocationHandler {
    
    private final Map<String, Object> arguments;
    
    ArgumentProxy(Map<String, Object> arguments) {
      this.arguments = arguments;
    }
    
    public Object invoke(Object proxy, Method method, Object[] args)
        throws Throwable {
      
      if (args != null)
        throw new IllegalStateException();
      
      return arguments.get(method.getName());
    }
  }
  
  private static final Map<Class<?>, ArgumentFactory> argumentFactories;
  
  static {
    Map<Class<?>, ArgumentFactory> factories = new HashMap<Class<?>, ArgumentParser.ArgumentFactory>();
    factories.put(Integer.class, new IntegerArgumentFactory());
    factories.put(Boolean.class, new BooleanArgumentFactory());
    factories.put(String.class, new StringArgumentFactory());
    factories.put(File.class, new FileArgumentFactory());
    factories.put(Charset.class, new CharsetArgumentFactory());
    
    argumentFactories = Collections.unmodifiableMap(factories);
  }
  
  private ArgumentParser() {
  }
  
  private static <T> void checkProxyInterface(Class<T> proxyInterface) {
    if (!proxyInterface.isInterface())
      throw new IllegalArgumentException("proxy interface is not an interface!");
    
    // all checks should also be performed for super interfaces
    
    Method methods[] = proxyInterface.getMethods();
    
    if (methods.length == 0)
      throw new IllegalArgumentException("proxy interface must at least declare one method!");
    
    for (Method method : methods) {
      
      // check that method names start with get
      if (!method.getName().startsWith("get") && method.getName().length() > 3) 
        throw new IllegalArgumentException(method.getName() + " method name does not start with get!");
    
      // check that method has zero arguments
      if (method.getParameterTypes().length != 0)
        throw new IllegalArgumentException(method.getName() + " method must have zero parameters!");
      
      // check return types of interface
      Class<?> returnType = method.getReturnType();
      
      Set<Class<?>> compatibleReturnTypes = argumentFactories.keySet();
      
      if(!compatibleReturnTypes.contains(returnType))
         throw new IllegalArgumentException(method.getName() + " method must have compatible return type!");
    }
  }
  
  private static String methodNameToParameter(String methodName) {
    // remove get from method name
    char parameterNameChars[] = methodName.toCharArray();
    
    // name length is checked to be at least 4 prior
    parameterNameChars[3] = Character.toLowerCase(parameterNameChars[3]);
    
    String parameterName = "-" + new String(parameterNameChars).substring(3);
    
    return parameterName;
  }
  
  /**
   * Creates a usage string which can be printed in case the user did specify the arguments
   * incorrectly. Incorrectly is defined as {@link ArgumentParser#validateArguments(String[], Class)}
   * returns false.
   * 
   * @param argProxyInterface
   * 
   * @return the help message usage string
   */
  public static <T> String createUsage(Class<T> argProxyInterface) {

    checkProxyInterface(argProxyInterface);
    
    StringBuilder usage = new StringBuilder();
    StringBuilder details = new StringBuilder();
    
    for (Method method : argProxyInterface.getMethods()) {
      
      ParameterDescription desc = method.getAnnotation(ParameterDescription.class);
      
      OptionalParameter optional = method.getAnnotation(OptionalParameter.class);
      
      if (desc != null) {
        String paramName = methodNameToParameter(method.getName());
        
        if (optional != null)
          usage.append('[');
        
        usage.append(paramName).append(' ').append(desc.valueName());
        details.append('\t').append(paramName).append(' ').append(desc.valueName()).append('\n');
        if(desc.description() != null && desc.description().length() > 0) {
          details.append("\t\t").append(desc.description()).append('\n');
        }
        
        if (optional != null)
          usage.append(']');
        
        usage.append(' ');
      }
    }
    
    if (usage.length() > 0)
      usage.setLength(usage.length() - 1);
    
    if(details.length() > 0) {
      details.setLength(details.length() - 1);
      usage.append("\n\nArguments description:\n").append(details.toString());
    }
    
    return usage.toString();
  }
  
  /**
   * Tests if the argument are correct or incorrect. Incorrect means, that mandatory arguments are missing or
   * there are unknown arguments. The argument value itself can also be incorrect, but this
   * is checked by the {@link ArgumentParser#parse(String[], Class)} method and reported accordingly.
   * 
   * @param args
   * @param argProxyInterface
   * @return
   */
  public static <T> boolean validateArguments(String args[], Class<T> argProxyInterface) {
    
    // number of parameters must be at least 2 and always be even
    if (args.length < 2 || args.length % 2 != 0)
      return false;
    
    int argumentCount = 0;
    
    for (Method method : argProxyInterface.getMethods()) {
      
      String valueString = CmdLineUtil.getParameter(
          methodNameToParameter(method.getName()), args);
      
      if (valueString == null) {
        OptionalParameter optionalParam = method.getAnnotation(OptionalParameter.class);
        
        // missing mandatory parameter
        if (optionalParam == null)
          return false;
      }
      else {
        argumentCount++;
      }
    }
    
    if (args.length / 2 != argumentCount)
      return false;
    
    return true;
  }
  
  /**
   * Parses the passed arguments and creates an instance of the proxy interface.
   * <p>
   * In case an argument value cannot be parsed a {@link TerminateToolException} is
   * thrown which contains an error message which explains the problems.
   * 
   * @param args
   * @param argProxyInterface
   * 
   * @return
   * 
   * @throws TerminateToolException if an argument value cannot be parsed.
   * @throws IllegalArgumentException if validateArguments returns false, if the proxy interface is not compatible.
   */
  @SuppressWarnings("unchecked")
  public static <T> T parse(String args[], Class<T> argProxyInterface) {
    
    checkProxyInterface(argProxyInterface);
    
    if (!validateArguments(args, argProxyInterface))
      throw new IllegalArgumentException("Passed args must be valid!");
    
    Map<String, Object> arguments = new HashMap<String, Object>();
    
    for (Method method : argProxyInterface.getMethods()) {
      
      String parameterName = methodNameToParameter(method.getName());
      String valueString = CmdLineUtil.getParameter(parameterName, args);
      
      if (valueString == null) {
        OptionalParameter optionalParam = method.getAnnotation(OptionalParameter.class);
        
        if (optionalParam.defaultValue().length() > 0)
          valueString = optionalParam.defaultValue();
        else
          valueString = null;
      }
      
      Class<?> returnType = method.getReturnType();
      
      Object value;
      
      if (valueString != null) {
        ArgumentFactory factory = argumentFactories.get(returnType);
        
        if (factory == null)
          throw new IllegalStateException();
        
        value = factory.parseArgument(method, parameterName, valueString);
      }
      else
        value = null;
      
      arguments.put(method.getName(), value);
    }
    
    return (T) java.lang.reflect.Proxy.newProxyInstance(
        argProxyInterface.getClassLoader(),
        new Class[]{argProxyInterface},
        new ArgumentProxy(arguments));
  }
}
