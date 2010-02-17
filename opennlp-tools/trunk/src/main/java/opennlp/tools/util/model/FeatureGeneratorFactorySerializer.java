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
import opennlp.tools.util.featuregen.FeatureGeneratorFactory;

@Deprecated
public class FeatureGeneratorFactorySerializer 
    implements ArtifactSerializer<FeatureGeneratorFactory>{

  private ClassSerializer classSerializer;
  
  public FeatureGeneratorFactorySerializer() {
    classSerializer = new ClassSerializer();
  }

  public FeatureGeneratorFactory create(InputStream in) throws IOException,
      InvalidFormatException {
    
    Class<?> generatorFactoryClass = classSerializer.create(in);
    
    try {
      return (FeatureGeneratorFactory) generatorFactoryClass.newInstance();
    } catch (InstantiationException e) {
      throw new InvalidFormatException(e);
    } catch (IllegalAccessException e) {
      throw new InvalidFormatException(e);
    }
  }

  public void serialize(FeatureGeneratorFactory artifact, OutputStream out)
      throws IOException {
    classSerializer.serialize(artifact.getClass(), out);
  }
}
