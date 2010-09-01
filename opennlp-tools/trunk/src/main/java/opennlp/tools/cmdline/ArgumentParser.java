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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
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
 * - Allowed return types are Integer, Boolean and String<br>
 * 
 * <p>
 * 
 * Note: Do not use this class, internal use only!
 */
public class ArgumentParser implements InvocationHandler {

  public @Retention(RetentionPolicy.RUNTIME)
  @interface OptionalParameter {
    public String defaultValue() default "";
  }
  
  public @Retention(RetentionPolicy.RUNTIME)
  @interface ParameterDescription {
    public String valueName();
    public String description() default "";
  }
  
  
  private final Map<String, Object> arguments;
  
  private ArgumentParser(Map<String, Object> arguments) {
    this.arguments = arguments;
  }
  
  public Object invoke(Object proxy, Method method, Object[] args)
      throws Throwable {
    
    if (args != null)
      throw new IllegalStateException();
    
    return arguments.get(method.getName());
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
      
      Set<Class<?>> compatibleReturnTypes = new HashSet<Class<?>>();
      compatibleReturnTypes.add(Integer.class);
      compatibleReturnTypes.add(Boolean.class);
      compatibleReturnTypes.add(String.class);
      
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
  
  public static <T> String createUsage(Class<T> argProxyInterface) {

    checkProxyInterface(argProxyInterface);
    
    StringBuilder usage = new StringBuilder();
    
    for (Method method : argProxyInterface.getMethods()) {
      
      ParameterDescription desc = method.getAnnotation(ParameterDescription.class);
      
      OptionalParameter optional = method.getAnnotation(OptionalParameter.class);
      
      if (desc != null) {
        
        if (optional != null)
          usage.append('[');
        
        usage.append(methodNameToParameter(method.getName()));
        usage.append(' ');
        usage.append(desc.valueName());
        
        if (optional != null)
          usage.append(']');
        
        usage.append(' ');
      }
    }
    
    if (usage.length() > 0)
      usage.setLength(usage.length() - 1);
    
    return usage.toString();
  }
  
  /**
   * Converts the options to their method names and maps
   * the method names to their return value.
   * 
   * @return the mapping or null if arguments are invalid
   */
  private static <T> Map<String, Object> createArgumentMap(String args[], Class<T> argProxyInterface) {
    
    // number of parameters must be at least 2 and always be even
    if (args.length < 2 || args.length % 2 != 0)
      return null;
    
    // create argument map
    Map<String, Object> arguments = new HashMap<String, Object>();
    
    for (Method method : argProxyInterface.getMethods()) {
      
      String valueString = CmdLineUtil.getParameter(
          methodNameToParameter(method.getName()), args);
      
      if (valueString == null) {
        OptionalParameter optionalParam = method.getAnnotation(OptionalParameter.class);
        
        // missing mandatory parameter
        if (optionalParam == null)
          return null;
        
        if (optionalParam.defaultValue().length() > 0)
          valueString = optionalParam.defaultValue();
        else
          valueString = null;
      }
      
      Class<?> returnType = method.getReturnType();
      
      Object value;
      
      if (valueString != null) {
        if (Integer.class.equals(returnType)) {
          try {
          value = Integer.parseInt(valueString);
          }
          catch (NumberFormatException e) {
            // parameter is not a number
            return null;
          }
        }
        else if (Boolean.class.equals(returnType)) {
          value = Boolean.parseBoolean(valueString);
        }
        else if (String.class.equals(returnType)) {
          value = valueString;
        }
        else {
          throw new IllegalStateException();
        }
      }
      else
        value = null;
      
      arguments.put(method.getName(), value);
    }
    
    return arguments;
  }
  
  public static <T> boolean validateArguments(String args[], Class<T> argProxyInterface) {
    return createArgumentMap(args, argProxyInterface) != null;
  }
  
  @SuppressWarnings("unchecked")
  public static <T> T parse(String args[], Class<T> argProxyInterface) {
    
    checkProxyInterface(argProxyInterface);
    
    Map<String, Object> argumentMap = createArgumentMap(args, argProxyInterface);
    
    if (argumentMap != null) {
      return (T) java.lang.reflect.Proxy.newProxyInstance(
          argProxyInterface.getClassLoader(),
          new Class[]{argProxyInterface},
          new ArgumentParser(argumentMap));
    }
    else {
      return null;
    }
  }
}
