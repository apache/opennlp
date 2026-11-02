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
import opennlp.tools.util.ext.ExtensionLoader;

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

  /**
   * Creates the delegate reader for the given {@link AlgorithmType}. The reader class is
   * resolved via {@link ExtensionLoader#loadExtensionClass(Class, String)}, so it has to
   * reside in a package permitted by the {@link ExtensionLoader}.
   *
   * @param type The {@link AlgorithmType} to create a reader for.
   * @return The delegate {@link AbstractModelReader}.
   * @throws opennlp.tools.util.ext.ExtensionNotLoadedException Thrown if the reader class is
   *         not in an allowed package or is not an {@link AbstractModelReader}.
   * @throws RuntimeException Thrown if the reader class is not available or could not be
   *         instantiated.
   */
  private AbstractModelReader fromType(AlgorithmType type) {
    try {
      final Class<? extends AbstractModelReader> readerClass =
          ExtensionLoader.loadExtensionClass(AbstractModelReader.class, type.getReaderClazz());

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
