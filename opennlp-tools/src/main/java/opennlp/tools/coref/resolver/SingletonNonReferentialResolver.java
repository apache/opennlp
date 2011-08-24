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


package opennlp.tools.coref.resolver;

import java.io.IOException;

/**
 * This class allows you to share a single instance of a non-referential resolver
 * among several resolvers.
 */
public class SingletonNonReferentialResolver extends DefaultNonReferentialResolver {

  private static SingletonNonReferentialResolver resolver;
  private static boolean trained;

  private SingletonNonReferentialResolver(String projectName, ResolverMode mode) throws IOException {
    super(projectName, "nonref", mode);
  }

  public static SingletonNonReferentialResolver getInstance(String modelName, ResolverMode mode) throws IOException {
    if (resolver == null) {
      resolver = new SingletonNonReferentialResolver(modelName, mode);
    }
    return resolver;
  }


  @Override
  public void train() throws IOException {
    if (!trained) {
      super.train();
      trained = true;
    }
  }
}
