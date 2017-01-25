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

package opennlp.tools.formats.brat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;

/**
 * Reads the annotations from the brat .ann annotation file.
 */
public class BratAnnotationStream implements ObjectStream<BratAnnotation> {

  static abstract class BratAnnotationParser {

    static final int ID_OFFSET = 0;
    static final int TYPE_OFFSET = 1;

    BratAnnotation parse(Span tokens[], CharSequence line) throws IOException {
      return null;
    }

    protected int parseInt(String intString) throws InvalidFormatException {
      try {
        return Integer.parseInt(intString);
      }
      catch (NumberFormatException e) {
        throw new InvalidFormatException(e);
      }
    }
  }

  static class SpanAnnotationParser extends BratAnnotationParser {

    private static final int BEGIN_OFFSET = 2;
    private static final int END_OFFSET = 3;

    @Override
    BratAnnotation parse(Span values[], CharSequence line) throws IOException {

      if (values.length > 4) {
        String type = values[BratAnnotationParser.TYPE_OFFSET].getCoveredText(line).toString();

        int endOffset = -1;

        int firstTextTokenIndex = -1;

        for (int i = END_OFFSET; i < values.length; i++) {
          if (!values[i].getCoveredText(line).toString().contains(";")) {
            endOffset = parseInt(values[i].getCoveredText(line).toString());
            firstTextTokenIndex = i + 1;
            break;
          }
        }

        String id = values[BratAnnotationParser.ID_OFFSET].getCoveredText(line).toString();

        String coveredText = line.subSequence(values[firstTextTokenIndex].getStart(),
            values[values.length - 1].getEnd()).toString();

        try {
          return new SpanAnnotation(id, type, new Span(parseInt(values[BEGIN_OFFSET]
              .getCoveredText(line).toString()), endOffset, type), coveredText);
        }
        catch (IllegalArgumentException e) {
          throw new InvalidFormatException(e);
        }
      }
      else {
        throw new InvalidFormatException("Line must have at least 5 fields");
      }
    }
  }

  static class RelationAnnotationParser extends BratAnnotationParser {

    private static final int ARG1_OFFSET = 2;
    private static final int ARG2_OFFSET = 3;

    private String parseArg(String arg) throws InvalidFormatException {
      if (arg.length() > 4) {
        return arg.substring(5).trim();
      }
      else {
        throw new InvalidFormatException("Failed to parse argument: " + arg);
      }
    }

    @Override
    BratAnnotation parse(Span tokens[], CharSequence line) throws IOException {
      return new RelationAnnotation(tokens[BratAnnotationParser.ID_OFFSET].getCoveredText(line).toString(),
          tokens[BratAnnotationParser.TYPE_OFFSET].getCoveredText(line).toString(),
          parseArg(tokens[ARG1_OFFSET].getCoveredText(line).toString()),
          parseArg(tokens[ARG2_OFFSET].getCoveredText(line).toString()));
    }
  }

  static class EventAnnotationParser extends BratAnnotationParser {

    @Override
    BratAnnotation parse(Span tokens[], CharSequence line) throws IOException {

      String[] typeParts = tokens[TYPE_OFFSET].getCoveredText(line).toString().split(":");

      if (typeParts.length != 2) {
        throw new InvalidFormatException(String.format(
            "Failed to parse [%s], type part must be in the format type:trigger", line));
      }

      String type = typeParts[0];
      String eventTrigger = typeParts[1];

      Map<String, String> arguments = new HashMap<>();

      for (int i = TYPE_OFFSET + 1; i < tokens.length; i++) {
        String[] parts = tokens[i].getCoveredText(line).toString().split(":");

        if (parts.length != 2) {
          throw new InvalidFormatException(String.format(
              "Failed to parse [%s], argument parts must be in form argument:value", line));
        }

        arguments.put(parts[0], parts[1]);
      }

      return new EventAnnotation(tokens[ID_OFFSET].getCoveredText(line).toString(),type, eventTrigger,
          arguments);
    }
  }

  static class AttributeAnnotationParser extends BratAnnotationParser {

    private static final int ATTACHED_TO_OFFSET = 2;
    private static final int VALUE_OFFSET = 3;

    @Override
    BratAnnotation parse(Span[] values, CharSequence line) throws IOException {

      if (values.length == 3 || values.length == 4) {

        String value = null;

        if (values.length == 4) {
          value = values[VALUE_OFFSET].getCoveredText(line).toString();
        }

        return new AttributeAnnotation(values[ID_OFFSET].getCoveredText(line).toString(),
            values[TYPE_OFFSET].getCoveredText(line).toString(),
            values[ATTACHED_TO_OFFSET].getCoveredText(line).toString(), value);
      }
      else {
        throw new InvalidFormatException("Line must have 3 or 4 fields");
      }
    }
  }

  private final AnnotationConfiguration config;
  private final BufferedReader reader;
  private final String id;

  BratAnnotationStream(AnnotationConfiguration config, String id, InputStream in) {
    this.config = config;
    this.id = id;

    reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
  }

  public BratAnnotation read() throws IOException {

    String line = reader.readLine();

    if (line != null) {
      Span tokens[] = WhitespaceTokenizer.INSTANCE.tokenizePos(line);

      if (tokens.length > 2) {
        String annId = tokens[BratAnnotationParser.ID_OFFSET].getCoveredText(line).toString();

        if (annId.length() == 0) {
          throw new InvalidFormatException("annotation id is empty");
        }

        // The first leter of the annotation id marks the annotation type

        final BratAnnotationParser parser;
        switch (annId.charAt(0)) {
          case 'T':
            parser = new SpanAnnotationParser();
            break;
          case 'R':
            parser = new RelationAnnotationParser();
            break;
          case 'A':
            parser = new AttributeAnnotationParser();
            break;
          case 'E':
            parser = new EventAnnotationParser();
            break;

          default:
            // Skip it, do that for everything unsupported (e.g. "*" id)
            return read();
        }

        if (parser == null) {
          throw new IOException("Failed to parse ann document with id " + id + ".ann and" +
              " type class, no parser registered: " + tokens[BratAnnotationParser.TYPE_OFFSET]
              .getCoveredText(line).toString());
        }

        try {
          return parser.parse(tokens, line);
        }
        catch (IOException e)  {
          throw new IOException(String.format("Failed to parse ann document with id [%s.ann]", id), e);
        }
      }
    }

    return null;
  }

  public void reset() throws IOException, UnsupportedOperationException {
    reader.reset();
  }

  public void close() throws IOException {
    reader.close();
  }
}
