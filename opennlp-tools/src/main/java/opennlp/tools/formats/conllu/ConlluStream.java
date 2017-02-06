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

package opennlp.tools.formats.conllu;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ParagraphStream;
import opennlp.tools.util.PlainTextByLineStream;

/**
 * The CoNNL-U Format is specified here:
 * http://universaldependencies.org/format.html
 */
public class ConlluStream implements ObjectStream<ConlluSentence> {
  private final ObjectStream<String> sentenceStream;

  public ConlluStream(InputStreamFactory in) throws IOException {
    this.sentenceStream = new ParagraphStream(new PlainTextByLineStream(in, StandardCharsets.UTF_8));
  }

  @Override
  public ConlluSentence read() throws IOException {
    String sentence = sentenceStream.read();

    if (sentence != null) {
      List<ConlluWordLine> wordLines = new ArrayList<>();

      BufferedReader reader = new BufferedReader(new StringReader(sentence));

      String line;
      while ((line = reader.readLine())  != null) {
        // # indicates a comment line and should be skipped
        if (!line.trim().startsWith("#")) {
          wordLines.add(new ConlluWordLine(line));
        }
      }

      return new ConlluSentence(wordLines);
    }

    return null;
  }

  @Override
  public void close() throws IOException {
    sentenceStream.close();
  }

  @Override
  public void reset() throws IOException, UnsupportedOperationException {
    sentenceStream.reset();
  }
}
