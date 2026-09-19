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

package opennlp.tools.ml.perceptron;

import opennlp.tools.commons.Internal;
import opennlp.tools.ml.model.ModelReaderFactory;
import opennlp.tools.ml.model.ModelWriterFactory;
import opennlp.tools.util.ext.ExtensionRegistrar;
import opennlp.tools.util.ext.ExtensionRegistry;

/**
 * Registers the trainers, model reader and model writer of opennlp-ml-perceptron.
 * <p>
 * Registered through {@code META-INF/services}. Do not use this class, internal use only!
 */
@Internal
public class PerceptronExtensionRegistrar implements ExtensionRegistrar {

  @Override
  public void register(ExtensionRegistry registry) {
    registry.register(PerceptronTrainer.class, PerceptronTrainer::new);
    registry.register(SimplePerceptronSequenceTrainer.class, SimplePerceptronSequenceTrainer::new);
    registry.register(PerceptronModelReader.class, ModelReaderFactory.class, PerceptronModelReader::new);
    registry.register(BinaryPerceptronModelWriter.class, ModelWriterFactory.class,
        BinaryPerceptronModelWriter::new);
  }
}
