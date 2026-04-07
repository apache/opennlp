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

package opennlp.tools.tokenize;

import java.util.List;

/**
 * Spanish-language realistic BPE tokenization integration tests.
 *
 * @see AbstractBPETokenizerRealisticTest
 */
public class BPETokenizerRealisticEsTest
    extends AbstractBPETokenizerRealisticTest {

  @Override
  List<String> getTrainingCorpus() {
    return List.of(
        "Ayer fui al mercado para comprar frutas y"
            + " verduras frescas",
        "El mercado estaba lleno de gente y los precios"
            + " eran buenos",
        "Las manzanas y las naranjas estaban muy frescas"
            + " y baratas",
        "Volví a casa y preparé una comida muy buena"
            + " para todos",
        "La comida estaba deliciosa y toda la familia"
            + " estaba contenta",
        "Después de la comida hicimos un paseo por el"
            + " parque grande",
        "El parque estaba muy bonito con los árboles"
            + " en flor",
        "Los niños jugaban en el jardín y los pájaros"
            + " cantaban",
        "Por la noche vimos una película muy buena"
            + " en la televisión",
        "La película era muy interesante y nos gustó"
            + " mucho a todos",
        "Mi amigo Carlos vive en una casa grande"
            + " en Madrid",
        "Él trabaja en una empresa de tecnología"
            + " desde hace cinco años",
        "Su esposa María es profesora en la universidad"
            + " central",
        "Tienen dos hijos que van a una escuela cerca"
            + " de la casa",
        "Los fines de semana les gusta hacer excursiones"
            + " por el campo",
        "Madrid es una ciudad muy bonita con una"
            + " historia muy rica",
        "La cocina española es conocida en todo el"
            + " mundo por su calidad",
        "Los museos de Madrid atraen a millones de"
            + " visitantes cada año",
        "El Prado es el museo más visitado de toda"
            + " la ciudad",
        "La vida en España es muy agradable y el clima"
            + " es muy bueno"
    );
  }

  @Override
  String getLanguageCode() {
    return "es";
  }

  @Override
  String getSimpleSentence() {
    return "La comida estaba deliciosa";
  }

  @Override
  String[] getSimpleSentenceExpectedWords() {
    return new String[] {
        "La", "comida", "estaba", "deliciosa"
    };
  }

  @Override
  List<String> getFrequentWords() {
    return List.of("muy", "los", "en");
  }

  @Override
  String getUnseenWord() {
    return "impresionante";
  }

  @Override
  String getSpanTestSentence() {
    return "Los niños jugaban en el jardín";
  }

  @Override
  String[] getSpanTestExpectedWords() {
    return new String[] {
        "Los", "niños", "jugaban", "en", "el", "jardín"
    };
  }

  @Override
  String getMultiWordSentence() {
    return "El parque estaba muy bonito con los árboles"
        + " en flor";
  }

  @Override
  String getSerializationTestSentence() {
    return "Ayer fui al mercado para comprar frutas"
        + " y verduras";
  }

  @Override
  String getConsistencyTestSentence() {
    return "Mi amigo Carlos vive en una casa grande"
        + " en Madrid";
  }

  @Override
  String getPunctuationTestSentence() {
    return "Hola, mundo!";
  }

  @Override
  String[] getExpectedPunctuationWords() {
    return new String[] {"Hola,", "mundo!"};
  }

  @Override
  String getCoarseTokenizationSentence() {
    return "La cocina española es conocida en todo"
        + " el mundo";
  }
}
