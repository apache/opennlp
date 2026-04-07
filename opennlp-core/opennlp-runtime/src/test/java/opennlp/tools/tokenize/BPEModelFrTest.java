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
 * French-specific tests for the {@link BPEModel} class.
 *
 * @see AbstractBPEModelTest
 * @see BPEModel
 */
public class BPEModelFrTest extends AbstractBPEModelTest {

  private static final List<String> CORPUS = List.of(
      "Le renard brun rapide saute par-dessus le chien paresseux, "
          + "qui dormait; il ne l'a jamais vu venir",
      "Le traitement du langage naturel est fascinant: "
          + "il combine la linguistique et les statistiques",
      "Après la pluie, le soleil est apparu; les enfants ont joué dehors, et les oiseaux ont chanté fort"
  );

  @Override
  protected List<String> getCorpus() {
    return CORPUS;
  }

  @Override
  protected String getLanguageCode() {
    return "fr";
  }
}
