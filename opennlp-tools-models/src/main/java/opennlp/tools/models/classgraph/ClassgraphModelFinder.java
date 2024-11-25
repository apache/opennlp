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
package opennlp.tools.models.classgraph;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ResourceList;
import io.github.classgraph.ScanResult;

import opennlp.tools.models.AbstractClassPathModelFinder;
import opennlp.tools.models.ClassPathModelFinder;

/**
 * Enables the detection of OpenNLP models in the classpath via classgraph.
 * By default, this class will search for JAR files starting with "opennlp-models-*".
 * This wildcard pattern can be adjusted by using the alternative constructor of this class.
 *
 * @implNote This implementation relies on the <a href="https://github.com/classgraph/classgraph">Classgraph</a> library.
 * When using this class, you have to take care of <i>Classgraph</i> being in the classpath
 * of your application, as it is declared as an optional dependency for <i>opennlp-tools-models</i>.
 */
public class ClassgraphModelFinder extends AbstractClassPathModelFinder implements ClassPathModelFinder {

  /**
   * By default, it scans for "opennlp-models-*.jar".
   */
  public ClassgraphModelFinder() {
    this(OPENNLP_MODEL_JAR_PREFIX);
  }

  /**
   * @param modelJarPrefix The leafnames of the jars that should be canned (e.g. "opennlp.jar").
   *                       May contain a wildcard glob ("opennlp-*.jar"). It must not be {@code null}.
   */
  public ClassgraphModelFinder(String modelJarPrefix) {
    super(modelJarPrefix);
  }

  /**
   * @apiNote The caller is responsible for closing it.
   *
   * @return A {@link ScanResult} ready for consumption.
   */
  @Override
  protected Object getContext() {
    return new ClassGraph().acceptJars(getJarModelPrefix()).disableDirScanning().scan();
  }

  /**
   * Attempts to obtain {@link URI URIs} from the classpath.
   * 
   * @param wildcardPattern The pattern to use for scanning. Must not be {@code null}.
   * @param context         An object holding context information. It might be {@code null}.
   * @return A list of matching classpath {@link URI URIs}. It may be empty if nothing is found.
   */
  @Override
  protected List<URI> getMatchingURIs(String wildcardPattern, Object context) {
    if (context instanceof ScanResult sr) {
      try (sr; final ResourceList resources = sr.getResourcesMatchingWildcard(wildcardPattern)) {
        return resources.getURIs();
      }
    }
    return Collections.emptyList();
  }
}
