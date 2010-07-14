/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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


package opennlp.tools.coref;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

import opennlp.maxent.io.BinaryGISModelReader;
import opennlp.model.AbstractModel;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.StringList;
import opennlp.tools.util.model.BaseModel;

public class CorefModel extends BaseModel {

  private static final String COMPONENT_NAME = "Coref";
  
  private static final String MALE_NAMES_DICTIONARY_ENTRY_NAME = "maleNames.dictionary";

  private static final String FEMALE_NAMES_DICTIONARY_ENTRY_NAME = "femaleNames.dictionary";

  private static final String NUMBER_MODEL_ENTRY_NAME = "number.model";

//  private Map<String, Set<String>> acronyms;

  private static final String COMMON_NOUN_RESOLVER_MODEL_ENTRY_NAME =
      "commonNounResolver.model";

  private static final String DEFINITE_NOUN_RESOLVER_MODEL_ENTRY_NAME =
      "definiteNounResolver.model";

  private static final String SPEECH_PRONOUN_RESOLVER_MODEL_ENTRY_NAME =
      "speechPronounResolver.model";

  // TODO: Add IModel

  private static final String PLURAL_NOUN_RESOLVER_MODEL_ENTRY_NAME =
      "pluralNounResolver.model";

  private static final String SINGULAR_PRONOUN_RESOLVER_MODEL_ENTRY_NAME =
      "singularPronounResolver.model";

  private static final String PROPER_NOUN_RESOLVER_MODEL_ENTRY_NAME =
      "properNounResolver.model";

  private static final String SIM_MODEL_ENTRY_NAME = "sim.model";

  private static final String PLURAL_PRONOUN_RESOLVER_MODEL_ENTRY_NAME =
      "pluralPronounResolver.model";

  public CorefModel(String languageCode, String project) throws IOException {
    super(COMPONENT_NAME, languageCode, null);

    artifactMap.put(MALE_NAMES_DICTIONARY_ENTRY_NAME,
        readNames(project + File.separator + "gen.mas"));

    artifactMap.put(FEMALE_NAMES_DICTIONARY_ENTRY_NAME,
        readNames(project + File.separator + "gen.fem"));

    // TODO: Create acronyms

    artifactMap.put(NUMBER_MODEL_ENTRY_NAME,
        createModel(project + File.separator + "num.bin.gz"));

    artifactMap.put(COMMON_NOUN_RESOLVER_MODEL_ENTRY_NAME,
        createModel(project + File.separator + "cmodel.bin.gz"));

    artifactMap.put(DEFINITE_NOUN_RESOLVER_MODEL_ENTRY_NAME,
        createModel(project + File.separator + "defmodel.bin.gz"));


    artifactMap.put(SPEECH_PRONOUN_RESOLVER_MODEL_ENTRY_NAME,
        createModel(project + File.separator + "fmodel.bin.gz"));

    // TODO: IModel

    artifactMap.put(PLURAL_NOUN_RESOLVER_MODEL_ENTRY_NAME,
        createModel(project + File.separator + "plmodel.bin.gz"));

    artifactMap.put(SINGULAR_PRONOUN_RESOLVER_MODEL_ENTRY_NAME,
        createModel(project + File.separator + "pmodel.bin.gz"));

    artifactMap.put(PROPER_NOUN_RESOLVER_MODEL_ENTRY_NAME,
        createModel(project + File.separator + "pnmodel.bin.gz"));

    artifactMap.put(SIM_MODEL_ENTRY_NAME,
        createModel(project + File.separator + "sim.bin.gz"));

    artifactMap.put(PLURAL_PRONOUN_RESOLVER_MODEL_ENTRY_NAME,
        createModel(project + File.separator + "tmodel.bin.gz"));
  }

  private AbstractModel createModel(String fileName) throws IOException {
    return new BinaryGISModelReader(new DataInputStream(new GZIPInputStream(
        new FileInputStream(fileName)))).getModel();
  }

  private static Dictionary readNames(String nameFile) throws IOException {
    Dictionary names = new Dictionary();

    BufferedReader nameReader = new BufferedReader(new FileReader(nameFile));
    for (String line = nameReader.readLine(); line != null; line = nameReader.readLine()) {
      names.put(new StringList(line));
    }

    return names;
  }

  public Dictionary getMaleNames() {
    return (Dictionary) artifactMap.get(MALE_NAMES_DICTIONARY_ENTRY_NAME);
  }

  public Dictionary getFemaleNames() {
    return (Dictionary) artifactMap.get(FEMALE_NAMES_DICTIONARY_ENTRY_NAME);
  }

  public AbstractModel getNumberModel() {
    return (AbstractModel) artifactMap.get(NUMBER_MODEL_ENTRY_NAME);
  }

//  public AcronymDictionary getAcronyms() {
//    return null;
//  }

  public AbstractModel getCommonNounResolverModel() {
    return (AbstractModel) artifactMap.get(COMMON_NOUN_RESOLVER_MODEL_ENTRY_NAME);
  }

  public AbstractModel getDefiniteNounResolverModel() {
    return (AbstractModel) artifactMap.get(DEFINITE_NOUN_RESOLVER_MODEL_ENTRY_NAME);
  }

  public AbstractModel getSpeechPronounResolverModel() {
    return (AbstractModel) artifactMap.get(SPEECH_PRONOUN_RESOLVER_MODEL_ENTRY_NAME);
  }

  // TODO: Where is this model used ?
//  public AbstractModel getIModel() {
//    return null;
//  }

  public AbstractModel getPluralNounResolverModel() {
    return (AbstractModel) artifactMap.get(PLURAL_NOUN_RESOLVER_MODEL_ENTRY_NAME);
  }

  public AbstractModel getSingularPronounResolverModel() {
    return (AbstractModel) artifactMap.get(SINGULAR_PRONOUN_RESOLVER_MODEL_ENTRY_NAME);
  }

  public AbstractModel getProperNounResolverModel() {
    return (AbstractModel) artifactMap.get(PROPER_NOUN_RESOLVER_MODEL_ENTRY_NAME);
  }

  public AbstractModel getSimModel() {
    return (AbstractModel) artifactMap.get(SIM_MODEL_ENTRY_NAME);
  }

  public AbstractModel getPluralPronounResolverModel() {
    return (AbstractModel) artifactMap.get(PLURAL_PRONOUN_RESOLVER_MODEL_ENTRY_NAME);
  }

  public static void main(String[] args) throws IOException {

    if (args.length != 1) {
      System.err.println("Usage: CorefModel projectDirectory");
      System.exit(-1);
    }

    String projectDirectory = args[0];

    CorefModel model = new CorefModel("en", projectDirectory);
    model.serialize(new FileOutputStream("coref.model"));
  }
}
