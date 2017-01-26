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
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.StringUtil;

/**
 * Parser for the training files of the BioNLP/NLPBA 2004 shared task.
 * <p>
 * The data contains five named entity types: DNA, RNA, protein, cell_type and cell_line.<br>
 * <p>
 * Data can be found on this web site:<br>
 * http://www-tsujii.is.s.u-tokyo.ac.jp/GENIA/ERtask/report.html
 * <p>
 * <b>Note:</b> Do not use this class, internal use only!
 */
public class BioNLP2004NameSampleStream implements ObjectStream<NameSample> {

  public static final int GENERATE_DNA_ENTITIES = 0x01;
  public static final int GENERATE_PROTEIN_ENTITIES = 0x01 << 1;
  public static final int GENERATE_CELLTYPE_ENTITIES = 0x01 << 2;
  public static final int GENERATE_CELLLINE_ENTITIES = 0x01 << 3;
  public static final int GENERATE_RNA_ENTITIES = 0x01 << 4;

  private final int types;

  private final ObjectStream<String> lineStream;

  public BioNLP2004NameSampleStream(InputStreamFactory in, int types) throws IOException {
    try {
      this.lineStream = new PlainTextByLineStream(in, StandardCharsets.UTF_8);
      System.setOut(new PrintStream(System.out, true, "UTF-8"));
    } catch (UnsupportedEncodingException e) {
      // UTF-8 is available on all JVMs, will never happen
      throw new IllegalStateException(e);
    }

    this.types = types;

  }

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

      String fields[] = line.split("\t");

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

      return new NameSample(sentence.toArray(new String[sentence.size()]),
          names.toArray(new Span[names.size()]), isClearAdaptiveData);
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

  public void reset() throws IOException, UnsupportedOperationException {
    lineStream.reset();
  }

  public void close() throws IOException {
    lineStream.close();
  }
}
