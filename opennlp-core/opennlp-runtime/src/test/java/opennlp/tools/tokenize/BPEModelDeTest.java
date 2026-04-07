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
 * German-specific tests for the {@link BPEModel} class.
 *
 * @see AbstractBPEModelTest
 * @see BPEModel
 */
public class BPEModelDeTest extends AbstractBPEModelTest {

  private static final List<String> CORPUS = List.of(
      "Der schnelle braune Fuchs springt über den faulen Hund, "
          + "der gerade schlief; er hat ihn nicht kommen sehen",
      "Natürliche Sprachverarbeitung ist faszinierend: "
          + "sie verbindet Linguistik, Informatik und Statistik",
      "Nachdem der Regen aufgehört hatte, kam die Sonne heraus; "
          + "die Kinder spielten draußen und die Vögel sangen laut"
  );

  @Override
  protected List<String> getCorpus() {
    return CORPUS;
  }

  @Override
  protected String getLanguageCode() {
    return "de";
  }
}
