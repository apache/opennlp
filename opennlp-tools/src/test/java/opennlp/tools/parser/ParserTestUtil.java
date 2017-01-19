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

package opennlp.tools.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;

import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.parser.lang.en.HeadRules;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

public class ParserTestUtil {

  public static HeadRules createTestHeadRules() throws IOException {
    InputStream headRulesIn =
        ParserTestUtil.class.getResourceAsStream("/opennlp/tools/parser/en_head_rules");

    HeadRules headRules = new HeadRules(new BufferedReader(
        new InputStreamReader(headRulesIn, "UTF-8")));

    headRulesIn.close();

    return headRules;
  }

  public static ObjectStream<Parse> openTestTrainingData()
      throws IOException {

    ObjectStream<Parse> resetableSampleStream = new ObjectStream<Parse>() {

      private ObjectStream<Parse> samples;

      public void close() throws IOException {
        samples.close();
      }

      public Parse read() throws IOException {
        return samples.read();
      }

      public void reset() throws IOException {
        try {
          if (samples != null) {
            samples.close();
          }
          InputStreamFactory in = new ResourceAsStreamFactory(getClass(),
              "/opennlp/tools/parser/parser.train");
          samples = new ParseSampleStream(new PlainTextByLineStream(in, StandardCharsets.UTF_8));
        } catch (UnsupportedEncodingException e) {
          // Should never happen
          Assert.fail(e.getMessage());
        }
      }
    };

    resetableSampleStream.reset();

    return resetableSampleStream;
  }
}
