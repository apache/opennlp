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

package opennlp.tools.formats;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.ObjectStream;


public class TwentyNewsgroupSampleStream implements ObjectStream<DocumentSample> {

  private Tokenizer tokenizer;

  private Map<Path, String> catFileMap = new HashMap<>();
  private Iterator<Map.Entry<Path, String>> catFileTupleIterator;

  TwentyNewsgroupSampleStream(Tokenizer tokenizer, Path dataDir) throws IOException {
    this.tokenizer = tokenizer;

    for (Path dir : Files.newDirectoryStream(dataDir, entry -> Files.isDirectory(entry))) {
      for (Path file : Files.newDirectoryStream(dir)) {
        catFileMap.put(file, dir.getFileName().toString());
      }
    }

    reset();
  }

  @Override
  public DocumentSample read() throws IOException {

    if (catFileTupleIterator.hasNext()) {
      Map.Entry<Path, String> catFileTuple = catFileTupleIterator.next();

      String text = new String(Files.readAllBytes(catFileTuple.getKey()));
      return new DocumentSample(catFileTuple.getValue(), tokenizer.tokenize(text));
    }

    return null;
  }

  @Override
  public void reset() throws IOException, UnsupportedOperationException {
    catFileTupleIterator = catFileMap.entrySet().iterator();
  }

  @Override
  public void close() throws IOException {
  }
}
