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
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.PlainTextByLineStream;

/**
 * Stream filter to produce document samples out of a Leipzig sentences.txt file.
 * In the Leipzig corpus the encoding of the various sentences.txt file is defined by
 * the language. The language must be specified to produce the category tags and is used
 * to determine the correct input encoding.
 * <p>
 * The input text is tokenized with the {@link SimpleTokenizer}. The input text classified
 * by the language model must also be tokenized by the {@link SimpleTokenizer} to produce
 * exactly the same tokenization during testing and training.
 */
public class LeipzigDoccatSampleStream extends
    FilterObjectStream<String, DocumentSample> {

  private final Tokenizer tokenizer;

  private final String language;
  private final int sentencesPerDocument;

  /**
   * Creates a new LeipzigDoccatSampleStream with the specified parameters.
   *
   * @param language the Leipzig input sentences.txt file
   * @param sentencesPerDocument the number of sentences which
   *                             should be grouped into once {@link DocumentSample}
   * @param in the InputStream pointing to the contents of the sentences.txt input file
   * @throws IOException IOException
   */
  public LeipzigDoccatSampleStream(String language, int sentencesPerDocument, Tokenizer tokenizer,
                                   InputStreamFactory in) throws IOException {
    super(new PlainTextByLineStream(in, StandardCharsets.UTF_8));
    System.setOut(new PrintStream(System.out, true, "UTF-8"));
    this.language = language;
    this.sentencesPerDocument = sentencesPerDocument;
    this.tokenizer = tokenizer;
  }

  /**
   * Creates a new LeipzigDoccatSampleStream with the specified parameters.
   *
   * @param language the Leipzig input sentences.txt file
   * @param sentencesPerDocument the number of sentences which should be
   *                             grouped into once {@link DocumentSample}
   * @param in the InputStream pointing to the contents of the sentences.txt input file
   * @throws IOException IOException
   */
  public LeipzigDoccatSampleStream(String language, int sentencesPerDocument,
      InputStreamFactory in) throws IOException {
    this(language, sentencesPerDocument, SimpleTokenizer.INSTANCE, in);
  }

  public DocumentSample read() throws IOException {

    int count = 0;

    StringBuilder sampleText = new StringBuilder();

    String line;
    while (count < sentencesPerDocument && (line = samples.read()) != null) {

      String tokens[] = tokenizer.tokenize(line);

      if (tokens.length == 0) {
        throw new IOException("Empty lines are not allowed!");
      }

      // Always skip first token, that is the sentence number!
      for (int i = 1; i < tokens.length; i++) {
        sampleText.append(tokens[i]);
        sampleText.append(' ');
      }

      count++;
    }


    if (sampleText.length() > 0) {
      return new DocumentSample(language, sampleText.toString());
    }

    return null;
  }
}
