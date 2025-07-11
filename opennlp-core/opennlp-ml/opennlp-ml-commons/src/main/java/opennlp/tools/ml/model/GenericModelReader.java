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

package opennlp.tools.ml.model;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import opennlp.tools.ml.AlgorithmType;

/**
 * An generic {@link AbstractModelReader} implementation.
 *
 * @see AbstractModelReader
 */
public class GenericModelReader extends AbstractModelReader {

  private AbstractModelReader delegateModelReader;

  /**
   * Initializes a {@link GenericModelReader} via a {@link File}.
   *
   * @param f The {@link File} that references the model to be read.
   * @throws IOException Thrown if IO errors occurred.
   */
  public GenericModelReader(File f) throws IOException {
    super(f);
  }

  /**
   * Initializes a {@link GenericModelReader} via a {@link DataReader}.
   *
   * @param dataReader The {@link DataReader} that references the model to be read.
   */
  public GenericModelReader(DataReader dataReader) {
    super(dataReader);
  }

  @Override
  public void checkModelType() throws IOException {
    this.delegateModelReader = fromType(AlgorithmType.fromModelType(readUTF()));
  }

  private AbstractModelReader fromType(AlgorithmType type) {
    try {
      final Class<? extends AbstractModelReader> readerClass
          = (Class<? extends AbstractModelReader>) Class.forName(type.getReaderClazz());

      return readerClass.getDeclaredConstructor(DataReader.class).newInstance(this.dataReader);

    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Given reader is not available in the classpath!", e);
    } catch (InvocationTargetException | InstantiationException | IllegalAccessException |
             NoSuchMethodException e) {
      throw new RuntimeException("Problem instantiating chosen reader class: " + type.getReaderClazz(), e);
    }
  }

  @Override
  public AbstractModel constructModel() throws IOException {
    return delegateModelReader.constructModel();
  }
}
