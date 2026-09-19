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

package opennlp.tools.ml.maxent;

import opennlp.tools.commons.Internal;
import opennlp.tools.ml.maxent.io.BinaryGISModelWriter;
import opennlp.tools.ml.maxent.io.BinaryQNModelWriter;
import opennlp.tools.ml.maxent.io.GISModelReader;
import opennlp.tools.ml.maxent.io.QNModelReader;
import opennlp.tools.ml.maxent.quasinewton.QNTrainer;
import opennlp.tools.ml.model.ModelReaderFactory;
import opennlp.tools.ml.model.ModelWriterFactory;
import opennlp.tools.util.ext.ExtensionRegistrar;
import opennlp.tools.util.ext.ExtensionRegistry;

/**
 * Registers the trainers, model readers and model writers of opennlp-ml-maxent.
 * <p>
 * Registered through {@code META-INF/services}. Do not use this class, internal use only!
 */
@Internal
public class MaxentExtensionRegistrar implements ExtensionRegistrar {

  @Override
  public void register(ExtensionRegistry registry) {
    registry.register(GISTrainer.class, GISTrainer::new);
    registry.register(QNTrainer.class, QNTrainer::new);
    registry.register(GISModelReader.class, ModelReaderFactory.class, GISModelReader::new);
    registry.register(QNModelReader.class, ModelReaderFactory.class, QNModelReader::new);
    registry.register(BinaryGISModelWriter.class, ModelWriterFactory.class, BinaryGISModelWriter::new);
    registry.register(BinaryQNModelWriter.class, ModelWriterFactory.class, BinaryQNModelWriter::new);
  }
}
