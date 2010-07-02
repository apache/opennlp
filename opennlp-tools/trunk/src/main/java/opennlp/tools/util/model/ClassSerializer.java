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


package opennlp.tools.util.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import opennlp.tools.util.InvalidFormatException;

@Deprecated
public class ClassSerializer implements ArtifactSerializer<Class<?>> {

  private static final String CLASS_SEARCH_NAME = "ClassSearchName";
  
  private byte[] classBytes;
  
  private static Class<?> loadClass(final byte[] classBytes)
      throws InvalidFormatException {

    ClassLoader loader = new ClassLoader() {
      @Override
      protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (CLASS_SEARCH_NAME.equals(name)) 
          return defineClass(null, classBytes, 0, classBytes.length);
        else
          return super.findClass(name);
      }
    };

    try {
      return loader.loadClass(CLASS_SEARCH_NAME);
    } catch (ClassNotFoundException e) {
      throw new InvalidFormatException(e);
    }
  }

  public Class<?> create(InputStream in) throws IOException,
      InvalidFormatException {
    classBytes = ModelUtil.read(in);

    Class<?> factoryClass = loadClass(classBytes);

    return factoryClass;
  }

  public void serialize(Class<?> artifact, OutputStream out) throws IOException {
    out.write(classBytes);
  }
}
