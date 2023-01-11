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

package opennlp.tools.ml.model;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.util.ObjectStream;

/**
 * Class for using a file of real-valued {@link Event events} as an
 * {@link ObjectStream event stream}.
 * The format of the file is one event per line with
 * each line consisting of outcome followed by contexts (space delimited).
 *
 * @see Event
 * @see FileEventStream
 */
public class RealValueFileEventStream extends FileEventStream {

  private static final Logger logger = LoggerFactory.getLogger(RealValueFileEventStream.class);

  /**
   * Instantiates a {@link RealValueFileEventStream} from the specified file name.
   *
   * @param fileName The name fo the file containing the events.
   *
   * @throws IOException Thrown if the specified file can not be read.
   */
  public RealValueFileEventStream(String fileName) throws IOException {
    super(fileName);
  }

  /**
   * Instantiates a {@link RealValueFileEventStream} from the specified file name.
   *
   * @param fileName The name fo the file containing the events.
   * @param encoding The name of the {@link java.nio.charset.Charset character encoding}.
   *
   * @throws IOException Thrown if the specified file can not be read.
   */
  public RealValueFileEventStream(String fileName, String encoding) throws IOException {
    super(fileName, encoding);
  }

  /**
   * Instantiates a {@link RealValueFileEventStream} via a {@link File}.
   *
   * @param file The {@link File} that holds events.
   *
   * @throws IOException Thrown if the specified file can not be read.
   */
  public RealValueFileEventStream(File file) throws IOException {
    super(file);
  }

  /**
   * Parses the specified contexts and re-populates context array with features
   * and returns the values for these features. If all values are unspecified,
   * then {@code null} is returned.
   *
   * @param contexts The contexts with real values specified.
   * @return The value for each context or null if all values are unspecified.
   */
  public static float[] parseContexts(String[] contexts) {
    boolean hasRealValue = false;
    float[] values = new float[contexts.length];
    for (int ci = 0; ci < contexts.length; ci++) {
      int ei = contexts[ci].lastIndexOf("=");
      if (ei > 0 && ei + 1 < contexts[ci].length()) {
        boolean gotReal = true;
        try {
          values[ci] = Float.parseFloat(contexts[ci].substring(ei + 1));
        } catch (NumberFormatException e) {
          gotReal = false;
          logger.error("Unable to determine value in context: {}", contexts[ci]);
          values[ci] = 1;
        }
        if (gotReal) {
          if (values[ci] < 0) {
            throw new RuntimeException("Negative values are not allowed: " + contexts[ci]);
          }
          contexts[ci] = contexts[ci].substring(0, ei);
          hasRealValue = true;
        }
      } else {
        values[ci] = 1;
      }
    }
    if (!hasRealValue) {
      values = null;
    }
    return values;
  }

  @Override
  public Event read() throws IOException {
    String line;
    if ((line = reader.readLine()) != null) {
      int si = line.indexOf(' ');
      String outcome = line.substring(0, si);
      String[] contexts = line.substring(si + 1).split(" ");
      float[] values = parseContexts(contexts);
      return new Event(outcome, contexts, values);
    }

    return null;
  }
}
