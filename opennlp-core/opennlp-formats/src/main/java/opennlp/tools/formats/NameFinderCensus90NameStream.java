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
import java.nio.charset.Charset;
import java.util.Locale;

import opennlp.tools.commons.Internal;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.StringList;
import opennlp.tools.util.StringUtil;

/**
 * This class helps to read the US Census data from the files to build a
 * StringList for each dictionary entry in the name-finder dictionary.
 * The entries in the source file are as follows:
 * <p>
 *      SMITH          1.006  1.006      1
 * <ul>
 * <li>The first field is the name (in ALL CAPS).
 * <li>The next field is a frequency in percent.
 * <li>The next is a cumulative frequency in percent.
 * <li>The last is a ranking.
 * </ul>
 * <p>
 * <b>Note:</b>
 * Do not use this class, internal use only!
 */
@Internal
public class NameFinderCensus90NameStream implements ObjectStream<StringList> {

  private final Locale locale;
  private final Charset encoding;
  private final ObjectStream<String> lineStream;

  /**
   * Initializes a {@link NameFinderCensus90NameStream} via {@link ObjectStream<String>}.
   *
   * @param lineStream  An {@link ObjectStream<String>} that represents the
   *                    input file to be attached to this class.
   */
  public NameFinderCensus90NameStream(ObjectStream<String> lineStream) {
    this.locale = new Locale("en");   // locale is English
    this.encoding = Charset.defaultCharset();
    // todo how do we find the encoding for an already open ObjectStream() ?
    this.lineStream = lineStream;
  }

  /**
   * Initializes a {@link NameFinderCensus90NameStream} via an {@link InputStreamFactory}
   * and a {@link Charset}.
   * Opens an associated stream object with the specified encoding specified.
   *
   * @param in  The {@link InputStreamFactory} for the input file.
   * @param encoding  the {@link Charset} to apply to the input stream.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  public NameFinderCensus90NameStream(InputStreamFactory in, Charset encoding)
      throws IOException {
    this.locale = new Locale("en"); // locale is English
    this.encoding = encoding;
    this.lineStream = new PlainTextByLineStream(in, this.encoding);
  }

  @Override
  public StringList read() throws IOException {
    String line = lineStream.read();
    StringList name = null;

    if ((line != null) &&
        (!StringUtil.isEmpty(line))) {
      String name2;
      // find the location of the name separator in the line of data.
      int pos = line.indexOf(' ');
      if ((pos != -1)) {
        String parsed = line.substring(0, pos);
        // the data is in ALL CAPS ... so the easiest way is to convert
        // back to standard mixed case.
        if ((parsed.length() > 2) &&
            (parsed.startsWith("MC"))) {
          name2 = parsed.substring(0,1).toUpperCase(locale) +
                  parsed.substring(1,2).toLowerCase(locale) +
                  parsed.substring(2,3).toUpperCase(locale) +
                  parsed.substring(3).toLowerCase(locale);
        } else {
          name2 = parsed.substring(0,1).toUpperCase(locale) +
                  parsed.substring(1).toLowerCase(locale);
        }
        name = new StringList(new String[]{name2});
      }
    }

    return name;
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
