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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.commons.Internal;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.StringUtil;

/**
 * A {@link ObjectStream sample stream} for the training files of the
 * BioNLP/NLPBA 2004 shared task.
 * <p>
 * The data contains five named entity types:
 * <ul>
 *   <li>{@code DNA}</li>
 *   <li>{@code RNA}</li>
 *   <li>{@code protein}</li>
 *   <li>{@code cell_type}</li>
 *   <li>{@code cell_line}</li>
 * </ul>
 * <p>
 * Data can be found on this
 * <a href="http://www.geniaproject.org/shared-tasks/bionlp-jnlpba-shared-task-2004">website</a>,
 * or in
 * <a href="https://github.com/spyysalo/jnlpba">this repository</a>.
 * <p>
 * The BioNLP/NLPBA 2004 data were originally published here:
 * <p>
 * <a href="http://www-tsujii.is.s.u-tokyo.ac.jp/GENIA/ERtask/report.html">
 *   http://www-tsujii.is.s.u-tokyo.ac.jp/GENIA/ERtask/report.html</a>,
 * <p>
 * yet this page was gone when last checked in December 2022.
 * <p>
 * It looks like this repo contains a copy of the data located on the original page: 
 * The BioNLP 2004 seems to be related to http://www.geniaproject.org/shared-tasks/bionlp-jnlpba-shared-task-2004
 * <p>
 * <b>Note:</b>
 * Do not use this class, internal use only!
 */
@Internal
public class BioNLP2004NameSampleStream implements ObjectStream<NameSample> {

  public static final int GENERATE_DNA_ENTITIES = 0x01;
  public static final int GENERATE_PROTEIN_ENTITIES = 0x01 << 1;
  public static final int GENERATE_CELLTYPE_ENTITIES = 0x01 << 2;
  public static final int GENERATE_CELLLINE_ENTITIES = 0x01 << 3;
  public static final int GENERATE_RNA_ENTITIES = 0x01 << 4;

  private final int types;

  private final ObjectStream<String> lineStream;

  /**
   * Initializes a {@link BioNLP2004NameSampleStream}.
   *
   * @param in The {@link InputStreamFactory} to use.
   * @param types The types to use.
   *
   * @throws IOException Thrown if IO errors occurred during initialization.
   */
  public BioNLP2004NameSampleStream(InputStreamFactory in, int types) throws IOException {
    this.lineStream = new PlainTextByLineStream(in, StandardCharsets.UTF_8);
    this.types = types;
  }

  @Override
  public NameSample read() throws IOException {

    List<String> sentence = new ArrayList<>();
    List<String> tags = new ArrayList<>();

    boolean isClearAdaptiveData = false;

    // Empty line indicates end of sentence

    String line;
    while ((line = lineStream.read()) != null && !StringUtil.isEmpty(line.trim())) {

      if (line.startsWith("###MEDLINE:")) {
        isClearAdaptiveData = true;
        lineStream.read();
        continue;
      }

      if (line.contains("ABSTRACT TRUNCATED"))
        continue;

      String[] fields = line.split("\t");

      if (fields.length == 2) {
        sentence.add(fields[0]);
        tags.add(fields[1]);
      }
      else {
        throw new IOException("Expected two fields per line in training data, got " +
            fields.length + " for line '" + line + "'!");
      }
    }

    if (sentence.size() > 0) {

      // convert name tags into spans
      List<Span> names = new ArrayList<>();

      int beginIndex = -1;
      int endIndex = -1;
      for (int i = 0; i < tags.size(); i++) {

        String tag = tags.get(i);

        if (tag.endsWith("DNA") && (types & GENERATE_DNA_ENTITIES) == 0)
          tag = "O";

        if (tag.endsWith("protein") && (types & GENERATE_PROTEIN_ENTITIES) == 0)
          tag = "O";

        if (tag.endsWith("cell_type") && (types & GENERATE_CELLTYPE_ENTITIES) == 0)
          tag = "O";

        if (tag.endsWith("cell_line") && (types & GENERATE_CELLTYPE_ENTITIES) == 0)
          tag = "O";
        if (tag.endsWith("RNA") && (types & GENERATE_RNA_ENTITIES) == 0)
          tag = "O";

        if (tag.startsWith("B-")) {

          if (beginIndex != -1) {
            names.add(new Span(beginIndex, endIndex, tags.get(beginIndex).substring(2)));
            beginIndex = -1;
            endIndex = -1;
          }

          beginIndex = i;
          endIndex = i + 1;
        }
        else if (tag.startsWith("I-")) {
          endIndex++;
        }
        else if (tag.equals("O")) {
          if (beginIndex != -1) {
            names.add(new Span(beginIndex, endIndex, tags.get(beginIndex).substring(2)));
            beginIndex = -1;
            endIndex = -1;
          }
        }
        else {
          throw new IOException("Invalid tag: " + tag);
        }
      }

      // if one span remains, create it here
      if (beginIndex != -1)
        names.add(new Span(beginIndex, endIndex, tags.get(beginIndex).substring(2)));

      return new NameSample(sentence.toArray(new String[0]),
          names.toArray(new Span[0]), isClearAdaptiveData);
    }
    else if (line != null) {
      // Just filter out empty events, if two lines in a row are empty
      return read();
    }
    else {
      // source stream is not returning anymore lines
      return null;
    }
  }

  @Override
  public void reset() throws IOException, UnsupportedOperationException {
    lineStream.reset();
  }

  @Override
  public void close() throws IOException {
    lineStream.close();
  }
}
