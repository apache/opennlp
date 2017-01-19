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

package opennlp.tools.util.ext;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.util.tracker.ServiceTracker;

/**
 * OSGi bundle activator which can use an OSGi service as
 * an OpenNLP extension.
 * <p>
 * <b>Note:</b> Do not use this class, internal use only!
 */
public class OSGiExtensionLoader implements BundleActivator {

  private static OSGiExtensionLoader instance;

  private BundleContext context;

  public void start(BundleContext context) throws Exception {
    instance = this;
    this.context = context;
    ExtensionLoader.setOSGiAvailable();
  }

  public void stop(BundleContext context) throws Exception {
    instance = null;
    this.context = null;
  }

  /**
   * Retrieves the
   *
   * @param clazz
   * @param id
   * @return
   */
  <T> T getExtension(Class<T> clazz, String id) {

    if (context == null) {
      throw new IllegalStateException("OpenNLP Tools Bundle is not active!");
    }

    Filter filter;
    try {
      filter = FrameworkUtil.createFilter("(&(objectclass=" + clazz.getName() + ")(" +
          "opennlp" + "=" + id + "))");
    } catch (InvalidSyntaxException e) {
      // Might happen when the provided IDs are invalid in some way.
      throw new ExtensionNotLoadedException(e);
    }

    // NOTE: In 4.3 the parameters are <T, T>
    ServiceTracker extensionTracker = new ServiceTracker(context, filter, null);

    T extension = null;

    try {
      extensionTracker.open();

      try {
        extension = (T) extensionTracker.waitForService(30000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    } finally {
      extensionTracker.close();
    }

    if (extension == null) {
      throw new ExtensionNotLoadedException("No suitable extension found. Extension name: " + id);
    }

    return extension;
  }

  static OSGiExtensionLoader getInstance() {
    return instance;
  }
}
