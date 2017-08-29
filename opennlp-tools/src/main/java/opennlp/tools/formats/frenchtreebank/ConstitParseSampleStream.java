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

package opennlp.tools.formats.frenchtreebank;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.SAXParser;

import org.xml.sax.SAXException;

import opennlp.tools.parser.Parse;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.XmlUtil;

public class ConstitParseSampleStream extends FilterObjectStream<byte[], Parse> {

  private SAXParser saxParser;

  private List<Parse> parses = new ArrayList<>();

  protected ConstitParseSampleStream(ObjectStream<byte[]> samples) {
    super(samples);
    saxParser = XmlUtil.createSaxParser();
  }

  public Parse read() throws IOException {
    if (parses.isEmpty()) {
      byte[] xmlbytes = samples.read();

      if (xmlbytes != null) {

        List<Parse> producedParses = new ArrayList<>();
        try {
          saxParser.parse(new ByteArrayInputStream(xmlbytes),
              new ConstitDocumentHandler(producedParses));
        } catch (SAXException e) {
          //TODO update after Java6 upgrade
          throw new IOException(e.getMessage(), e);
        }

        parses.addAll(producedParses);
      }
    }

    if (parses.size() > 0) {
      return parses.remove(0);
    }
    return null;
  }
}
