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

package opennlp.tools.util.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.parser.ParserChunkerFactory;
import opennlp.tools.util.Version;


public class ChunkerModelSerializer implements ArtifactSerializer<ChunkerModel> {

  public ChunkerModel create(InputStream in) throws IOException {

    ChunkerModel model = new ChunkerModel(new UncloseableInputStream(in));

    Version version = model.getVersion();
    if (version.getMajor() == 1 && version.getMinor() == 5) {

      model = new ChunkerModel(model.getLanguage(), model.getChunkerModel(), new ParserChunkerFactory());

    }

    return model;
  }

  public void serialize(ChunkerModel artifact, OutputStream out)
      throws IOException {
    artifact.serialize(out);
  }
}
