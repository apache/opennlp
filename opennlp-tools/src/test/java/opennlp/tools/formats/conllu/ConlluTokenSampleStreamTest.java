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

package opennlp.tools.formats.conllu;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;

public class ConlluTokenSampleStreamTest {

  @Test
  public void testParseTwoSentences() throws IOException {
    InputStreamFactory streamFactory =
        new ResourceAsStreamFactory(ConlluStreamTest.class, "de-ud-train-sample.conllu");

    try (ObjectStream<TokenSample> stream = new ConlluTokenSampleStream(new ConlluStream(streamFactory))) {

      TokenSample expected1 = TokenSample.parse(
          "Fachlich kompetent" + TokenSample.DEFAULT_SEPARATOR_CHARS
              + ", sehr gute Beratung und ein freundliches Team" + TokenSample.DEFAULT_SEPARATOR_CHARS
              + ".", TokenSample.DEFAULT_SEPARATOR_CHARS);
      Assert.assertEquals(expected1, stream.read());

      TokenSample expected2 = TokenSample.parse("Beiden Zahnärzten verdanke ich einen " +
          "neuen Biss und dadurch endlich keine Rückenschmerzen mehr"
          + TokenSample.DEFAULT_SEPARATOR_CHARS + ".", TokenSample.DEFAULT_SEPARATOR_CHARS);
      Assert.assertEquals(expected2, stream.read());

      Assert.assertNull("Stream must be exhausted", stream.read());
    }
  }

  @Test
  public void testParseContraction() throws IOException {
    InputStreamFactory streamFactory =
        new ResourceAsStreamFactory(ConlluStreamTest.class, "pt_br-ud-sample.conllu");

    try (ObjectStream<TokenSample> stream = new ConlluTokenSampleStream(new ConlluStream(streamFactory))) {

      TokenSample expected1 = TokenSample.parse(
          "Numa reunião entre representantes da Secretaria da Criança do DF " +
              "ea juíza da Vara de Execuções de Medidas Socioeducativas" +
              TokenSample.DEFAULT_SEPARATOR_CHARS + ", Lavínia Tupi Vieira Fonseca" +
              TokenSample.DEFAULT_SEPARATOR_CHARS + ", ficou acordado que dos 25 internos" +
              TokenSample.DEFAULT_SEPARATOR_CHARS + ", 12 serão internados na Unidade de " +
              "Planaltina e os outros 13 devem retornar para a Unidade do Recanto das Emas" +
              TokenSample.DEFAULT_SEPARATOR_CHARS + ", antigo Ciago" +
              TokenSample.DEFAULT_SEPARATOR_CHARS + "."
          , TokenSample.DEFAULT_SEPARATOR_CHARS);
      TokenSample predicted = stream.read();
      Assert.assertEquals(expected1, predicted);
    }
  }

  @Test
  public void testParseSpanishS300() throws IOException {
    InputStreamFactory streamFactory =
        new ResourceAsStreamFactory(ConlluStreamTest.class, "es-ud-sample.conllu");

    try (ObjectStream<TokenSample> stream = new ConlluTokenSampleStream(new ConlluStream(streamFactory))) {

      TokenSample expected1 = TokenSample.parse(
          "Digámoslo claramente" + TokenSample.DEFAULT_SEPARATOR_CHARS +
              ", la insurgencia se ha pronunciado mucho más claramente respecto al " +
              "tema de la paz que el Estado" + TokenSample.DEFAULT_SEPARATOR_CHARS +
              ", como lo demuestra el fragmento que Bermúdez cita de la respuesta de \"" +
              TokenSample.DEFAULT_SEPARATOR_CHARS + "Gabino" +
              TokenSample.DEFAULT_SEPARATOR_CHARS + "\" a Piedad Córdoba" +
              TokenSample.DEFAULT_SEPARATOR_CHARS + ", en la cual no se plantea ni siquiera \"" +
              TokenSample.DEFAULT_SEPARATOR_CHARS + "esperar un mejor gobierno" +
              TokenSample.DEFAULT_SEPARATOR_CHARS + "\"" +
              TokenSample.DEFAULT_SEPARATOR_CHARS + "."

          , TokenSample.DEFAULT_SEPARATOR_CHARS);
      TokenSample predicted = stream.read();
      Assert.assertEquals(expected1, predicted);
    }
  }
}
