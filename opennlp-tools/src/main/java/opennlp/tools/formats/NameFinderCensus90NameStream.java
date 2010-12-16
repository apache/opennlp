/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  under the License.
 */

package opennlp.tools.formats;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Locale;

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
 * <p>
 * <ul>
 * <li>The first field is the name (in ALL CAPS).
 * <li>The next field is a frequency in percent.
 * <li>The next is a cumulative frequency in percent.
 * <li>The last is a ranking.
 * </ul>
 * <p>
 * <b>Note:</b> Do not use this class, internal use only!
 */
public class NameFinderCensus90NameStream implements ObjectStream<StringList> {

  private final Locale locale;
  private final Charset encoding;
  private final ObjectStream<String> lineStream;

  /**
   * This constructor takes an ObjectStream and initializes the class to handle
   * the stream.
   *
   * @param lineStream  an <code>ObjectSteam<String></code> that represents the
   *                    input file to be attached to this class.
   */
  public NameFinderCensus90NameStream(ObjectStream<String> lineStream) {
    this.locale = new Locale("en");   // locale is English
    this.encoding = Charset.defaultCharset();
    // todo how do we find the encoding for an already open ObjectStream() ?
    this.lineStream = lineStream;
  }

  /**
   * This constructor takes an <code>InputStream</code> and a <code>Charset</code>
   * and opens an associated stream object with the specified encoding specified.
   *
   * @param in  an <code>InputStream</code> for the input file.
   * @param encoding  the <code>Charset</code> to apply to the input stream.
   */
  public NameFinderCensus90NameStream(InputStream in, Charset encoding) {
    this.locale = new Locale("en");   // locale is English
    this.encoding = encoding;
    this.lineStream = new PlainTextByLineStream(in, this.encoding);
  }

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

  public void reset() throws IOException, UnsupportedOperationException {
    lineStream.reset();
  }

  public void close() throws IOException {
    lineStream.close();
  }

}
