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

package opennlp.uima.util;

import java.io.IOException;
import java.io.InputStream;

import org.apache.uima.resource.DataResource;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.SharedResourceObject;

public abstract class AbstractModelResource<T> implements SharedResourceObject {

  protected T model;

  protected abstract T loadModel(InputStream in) throws IOException;

  public void load(DataResource resource) throws ResourceInitializationException {
    try {
      model = loadModel(resource.getInputStream());
    } catch (IOException e) {
      throw new ResourceInitializationException(
          ExceptionMessages.MESSAGE_CATALOG,
          ExceptionMessages.IO_ERROR_MODEL_READING, new Object[] {
              e.getMessage()}, e);
    }
  }
}
