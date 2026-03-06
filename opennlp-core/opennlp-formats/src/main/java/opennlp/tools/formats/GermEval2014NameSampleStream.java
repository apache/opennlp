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
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.StringUtil;

/**
 * Parser for the GermEval 2014 Named Entity Recognition Shared Task data.
 * <p>
 * The data is in a tab-separated format with four columns:
 * <ol>
 *   <li>Token index (1-based per sentence)</li>
 *   <li>Token text</li>
 *   <li>Outer named entity tag (IOB2 scheme)</li>
 *   <li>Nested/embedded named entity tag (IOB2 scheme)</li>
 * </ol>
 * Comment lines starting with {@code #} mark document boundaries and contain
 * source URL and date metadata. Blank lines separate sentences.
 * <p>
 * The data uses four main entity types: Person (PER), Location (LOC),
 * Organization (ORG) and Other (OTH), with additional {@code deriv} and
 * {@code part} suffixes for derived forms and name parts respectively.
 * <p>
 * Since {@link NameSample} does not support overlapping spans, this stream
 * requires selecting either the {@link NerLayer#OUTER outer} or
 * {@link NerLayer#INNER inner} annotation layer via a {@link NerLayer} parameter.
 * <p>
 * Data can be found on
 * <a href="https://sites.google.com/site/germeval2014ner/data">this web site</a>.
 * <p>
 * <b>Note:</b>
 * Do not use this class, internal use only!
 */
@Internal
public class GermEval2014NameSampleStream implements ObjectStream<NameSample> {

  /**
   * Selects which NER annotation layer to read from the GermEval 2014 data.
   */
  public enum NerLayer {
    /** The outer (top-level) named entity annotations (column 3). */
    OUTER,
    /** The nested/embedded named entity annotations (column 4). */
    INNER
  }

  public static final int GENERATE_PERSON_ENTITIES = 0x01;
  public static final int GENERATE_ORGANIZATION_ENTITIES = 0x01 << 1;
  public static final int GENERATE_LOCATION_ENTITIES = 0x01 << 2;
  public static final int GENERATE_MISC_ENTITIES = 0x01 << 3;

  private final ObjectStream<String> lineStream;
  private final int types;
  private final NerLayer layer;

  /**
   * Initializes a {@link GermEval2014NameSampleStream}.
   *
   * @param lineStream An {@link ObjectStream} over the lines
   *                   in the GermEval 2014 data file.
   * @param types      The entity types to include in the Name Sample object stream.
   * @param layer      The {@link NerLayer} to read.
   */
  public GermEval2014NameSampleStream(final ObjectStream<String> lineStream,
                                      final int types, final NerLayer layer) {
    this.lineStream = lineStream;
    this.types = types;
    this.layer = layer;
  }

  /**
   * Initializes a {@link GermEval2014NameSampleStream}.
   *
   * @param in    The {@link InputStreamFactory} for the input file.
   * @param types The entity types to include in the Name Sample object stream.
   * @param layer The {@link NerLayer} to read.
   * @throws IOException Thrown if IO errors occurred.
   */
  public GermEval2014NameSampleStream(final InputStreamFactory in, final int types,
                                      final NerLayer layer) throws IOException {
    this(new PlainTextByLineStream(in, StandardCharsets.UTF_8), types, layer);
  }

  static Span extract(final int begin, final int end, final String beginTag)
      throws InvalidFormatException {

    final String type = mapTagToType(beginTag);
    return new Span(begin, end, type);
  }

  private static String mapTagToType(final String tag) throws InvalidFormatException {
    // Strip B- or I- prefix
    final String rawType = tag.substring(2);

    return switch (rawType) {
      case "PER" -> "person";
      case "PERderiv" -> "personderiv";
      case "PERpart" -> "personpart";
      case "LOC" -> "location";
      case "LOCderiv" -> "locationderiv";
      case "LOCpart" -> "locationpart";
      case "ORG" -> "organization";
      case "ORGderiv" -> "organizationderiv";
      case "ORGpart" -> "organizationpart";
      case "OTH" -> "misc";
      case "OTHderiv" -> "miscderiv";
      case "OTHpart" -> "miscpart";
      default -> throw new InvalidFormatException("Unknown type: " + rawType);
    };
  }

  private boolean isTypeEnabled(final String tag) {
    if (tag.startsWith("B-PER") || tag.startsWith("I-PER")) {
      return (types & GENERATE_PERSON_ENTITIES) != 0;
    }
    if (tag.startsWith("B-ORG") || tag.startsWith("I-ORG")) {
      return (types & GENERATE_ORGANIZATION_ENTITIES) != 0;
    }
    if (tag.startsWith("B-LOC") || tag.startsWith("I-LOC")) {
      return (types & GENERATE_LOCATION_ENTITIES) != 0;
    }
    if (tag.startsWith("B-OTH") || tag.startsWith("I-OTH")) {
      return (types & GENERATE_MISC_ENTITIES) != 0;
    }
    return tag.equals("O");
  }

  private List<Span> convertTagsToSpans(final List<String> tags) throws IOException {
    final List<Span> names = new ArrayList<>();

    int beginIndex = -1;
    int endIndex = -1;

    for (int i = 0; i < tags.size(); i++) {
      String tag = tags.get(i);

      if (!tag.equals("O") && !isTypeEnabled(tag)) {
        tag = "O";
      }

      if (tag.startsWith("B-")) {
        if (beginIndex != -1) {
          names.add(extract(beginIndex, endIndex, tags.get(beginIndex)));
        }
        beginIndex = i;
        endIndex = i + 1;
      } else if (tag.startsWith("I-")) {
        endIndex++;
      } else if (tag.equals("O")) {
        if (beginIndex != -1) {
          names.add(extract(beginIndex, endIndex, tags.get(beginIndex)));
          beginIndex = -1;
          endIndex = -1;
        }
      } else {
        throw new IOException("Invalid tag: " + tag);
      }
    }

    // if one span remains, create it here
    if (beginIndex != -1) {
      names.add(extract(beginIndex, endIndex, tags.get(beginIndex)));
    }

    return names;
  }

  @Override
  public NameSample read() throws IOException {

    final List<String> sentence = new ArrayList<>();
    final List<String> outerTags = new ArrayList<>();
    final List<String> innerTags = new ArrayList<>();

    boolean isClearAdaptiveData = false;

    // Empty line indicates end of sentence
    String line;
    while ((line = lineStream.read()) != null && !StringUtil.isEmpty(line)) {

      // Comment lines starting with # mark document boundaries
      if (line.startsWith("#")) {
        isClearAdaptiveData = true;
        continue;
      }

      final String[] fields = line.split("\t");

      if (fields.length >= 4) {
        sentence.add(fields[1]);
        outerTags.add(fields[2]);
        innerTags.add(fields[3].trim());
      } else {
        throw new IOException("Expected at least four tab-separated fields per line "
            + "in GermEval 2014 data, got " + fields.length + " for line '" + line + "'!");
      }
    }

    if (sentence.size() > 0) {
      final List<String> selectedTags = (layer == NerLayer.OUTER) ? outerTags : innerTags;
      final List<Span> names = convertTagsToSpans(selectedTags);

      return new NameSample(sentence.toArray(new String[0]),
          names.toArray(new Span[0]), isClearAdaptiveData);
    } else if (line != null) {
      // Just filter out empty events, if two lines in a row are empty
      return read();
    } else {
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
