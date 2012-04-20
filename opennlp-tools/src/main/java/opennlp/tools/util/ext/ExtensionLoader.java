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

package opennlp.tools.util.ext;

public class ExtensionLoader {

  private ExtensionLoader() {
  }
  
  // Pass in the type (interface) of the class to load
  /**
   * Instantiates an user provided extension to OpenNLP.
   * <p>
   * The extension is either loaded from the class path or if running
   * inside an OSGi environment via an OSGi service.
   * 
   * @param clazz
   * @param extensionClassName
   * 
   * @return
   */
  // TODO: Throw custom exception if loading fails ...
  @SuppressWarnings("unchecked")
  public static <T> T instantiateExtension(Class<T> clazz, String extensionClassName) {

    // First try to load extension and instantiate extension from class path
    try {
      Class<?> extClazz = Class.forName(extensionClassName);
      
      if (clazz.isAssignableFrom(extClazz)) {
        
        try {
          return (T) extClazz.newInstance();
        } catch (InstantiationException e) {
          throw new ExtensionNotLoadedError(e);
        } catch (IllegalAccessException e) {
          throw new ExtensionNotLoadedError(e);
        }
      }
      else {
        // throw exception ... class is not compatible ...
      }
    } catch (ClassNotFoundException e) {
      throw new ExtensionNotLoadedError(e);
    }
    
    // Loading from class path failed
   
    // Either something is wrong with the class name or OpenNLP is
    // running in an OSGi environment. The extension classes are not
    // on our classpath in this case.
    // In OSGi we need to use services to get access to extensions.
    
    // Determine if OSGi class is on class path

    boolean isOsgiAvailable;
    
    try {
      Class.forName("org.osgi.framework.ServiceReference");
      isOsgiAvailable = true;
    } catch (ClassNotFoundException e) {
      isOsgiAvailable = false;
    }
    
    // Now load class which depends on OSGi API
    if (isOsgiAvailable) {
      
      // The OSGIExtensionLoader class will be loaded when the next line
      // is executed, but not prior, and that is why it is safe to directly
      // reference it here.
      OSGiExtensionLoader extLoader = OSGiExtensionLoader.getInstance();
      return extLoader.findExtension(clazz, extensionClassName);
    }
    
    throw new ExtensionNotLoadedError("Unable to find implementation for " + 
          clazz.getName() + ", the class or service " + extensionClassName + 
          " could not be located!");
  }
}
