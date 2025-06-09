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

package opennlp.tools.formats.convert;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FileToByteArraySampleStreamTest extends AbstractConvertTest {

  @Test
  public void readFileTest() throws IOException {
    try (FileToByteArraySampleStream stream = new FileToByteArraySampleStream(sampleStream)) {
      byte[] read = stream.read();
      Assertions.assertTrue(sentences.contains(new String(read, StandardCharsets.UTF_8)));

      read = stream.read();
      Assertions.assertTrue(sentences.contains(new String(read, StandardCharsets.UTF_8)));
    }
  }

}
