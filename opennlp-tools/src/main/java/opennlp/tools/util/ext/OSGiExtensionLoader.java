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

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * OSGi bundle activator which can use an OSGi service as
 * an OpenNLP extension.
 */
public class OSGiExtensionLoader implements BundleActivator {

  private static OSGiExtensionLoader instance;
  
  private BundleContext context;
  
  public void start(BundleContext context) throws Exception {
    instance = this;
    this.context = context;
  }

  public void stop(BundleContext context) throws Exception {
    instance = null;
    this.context = null;
  }
  
  <T> T findExtension(Class<T> clazz, String id) {
    ServiceReference serviceRef = 
        context.getServiceReference(clazz.getName());
    
    return (T )context.getService(serviceRef);
  }

  public static OSGiExtensionLoader getInstance() {
    return instance;
  }
}
