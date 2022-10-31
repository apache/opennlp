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
import opennlp.tools.postag.POSSample;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;

public class ConlluPOSSampleStreamTest {
  @Test
  public void testParseContraction() throws IOException {
    InputStreamFactory streamFactory =
        new ResourceAsStreamFactory(ConlluStreamTest.class, "pt_br-ud-sample.conllu");

    try (ObjectStream<POSSample> stream = new ConlluPOSSampleStream(
        new ConlluStream(streamFactory), ConlluTagset.U)) {

      POSSample expected = POSSample.parse("Numa_ADP+DET reunião_NOUN entre_ADP " +
          "representantes_NOUN da_ADP+DET Secretaria_PROPN da_ADP+DET Criança_PROPN do_ADP+DET " +
          "DF_PROPN ea_CCONJ juíza_NOUN da_ADP+DET Vara_PROPN de_ADP Execuções_PROPN de_ADP " +
          "Medidas_PROPN Socioeducativas_PROPN ,_PUNCT Lavínia_PROPN Tupi_PROPN Vieira_PROPN " +
          "Fonseca_PROPN ,_PUNCT ficou_VERB acordado_ADJ que_CCONJ dos_ADP+DET 25_NUM " +
          "internos_NOUN ,_PUNCT 12_NUM serão_AUX internados_VERB na_ADP+DET Unidade_PROPN " +
          "de_ADP Planaltina_PROPN e_CCONJ os_DET outros_DET 13_NUM devem_AUX retornar_VERB " +
          "para_ADP a_DET Unidade_PROPN do_ADP+DET Recanto_NOUN das_ADP+DET Emas_PROPN ,_PUNCT " +
          "antigo_ADJ Ciago_PROPN ._PUNCT");

      POSSample predicted = stream.read();
      Assert.assertEquals(expected, predicted);
    }
  }


  @Test
  public void testParseSpanishS300() throws IOException {
    InputStreamFactory streamFactory =
        new ResourceAsStreamFactory(ConlluStreamTest.class, "es-ud-sample.conllu");

    try (ObjectStream<POSSample> stream = new ConlluPOSSampleStream(new ConlluStream(streamFactory),
        ConlluTagset.U)) {

      POSSample expected1 = POSSample.parse(
          "Digámoslo_VERB+PRON+PRON claramente_ADV ,_PUNCT la_DET insurgencia_NOUN se_PRON " +
              "ha_AUX pronunciado_VERB mucho_PRON más_ADV claramente_ADV respecto_NOUN " +
              "al_ADP+DET tema_NOUN de_ADP la_DET paz_NOUN que_CCONJ el_DET Estado_NOUN ,_PUNCT " +
              "como_SCONJ lo_PRON demuestra_VERB el_DET fragmento_NOUN que_SCONJ Bermúdez_PROPN " +
              "cita_VERB de_ADP la_DET respuesta_NOUN de_ADP \"_PUNCT Gabino_PROPN \"_PUNCT " +
              "a_ADP Piedad_PROPN Córdoba_PROPN ,_PUNCT en_ADP la_DET cual_PRON no_ADV se_PRON " +
              "plantea_VERB ni_CCONJ siquiera_ADV \"_PUNCT esperar_VERB un_DET mejor_ADJ " +
              "gobierno_NOUN \"_PUNCT ._PUNCT");
      POSSample predicted = stream.read();
      Assert.assertEquals(expected1, predicted);
    }
  }
}
