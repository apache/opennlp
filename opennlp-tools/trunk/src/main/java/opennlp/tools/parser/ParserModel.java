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


package opennlp.tools.parser;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import opennlp.maxent.io.BinaryGISModelReader;
import opennlp.model.AbstractModel;
import opennlp.model.BinaryFileDataReader;
import opennlp.model.GenericModelReader;
import opennlp.model.MaxentModel;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ModelUtil;

/**
 * This is an abstract base class for {@link ParserModel} implementations.
 */
public class ParserModel {

  private static final String BUILD_MODEL_ENTRY_NAME = "build.bin";

  private static final String CHECK_MODEL_ENTRY_NAME = "check.bin";

  private static final String PARSER_TAGGER_MODEL_ENTRY_NAME = "tagger";

  private static final String CHUNKER_TAGGER_MODEL_ENTRY_NAME = "chunker";

  private static final String HEAD_RULES_MODEL_ENTRY_NAME = "head-rules";

  private AbstractModel buildModel;

  private AbstractModel checkModel;

  private POSModel parserTagger;

  private ChunkerModel chunkerTagger;

  private opennlp.tools.parser.lang.en.HeadRules headRules;

  public ParserModel(AbstractModel buildModel, AbstractModel checkModel, POSModel parserTagger,
      ChunkerModel chunkerTagger, opennlp.tools.parser.lang.en.HeadRules headRules) {

    this.buildModel = buildModel;
    this.checkModel = checkModel;
    this.parserTagger = parserTagger;
    this.chunkerTagger = chunkerTagger;
    this.headRules = headRules;
  }

  public MaxentModel getBuildModel() {
    return buildModel;
  }

  public MaxentModel getCheckModel() {
    return checkModel;
  }

// only used by treeinsert parser
//
//  public MaxentModel getAttachModel() {
//    return null;
//  }

  public POSModel getParserTaggerModel() {
    return parserTagger;
  }

  public ChunkerModel getParserChunkerModel() {
    return chunkerTagger;
  }

  public opennlp.tools.parser.lang.en.HeadRules getHeadRules() {
    return headRules;
  }

  public void serialize(OutputStream out) throws IOException {
    ZipOutputStream zip = new ZipOutputStream(out);

    zip.putNextEntry(new ZipEntry(BUILD_MODEL_ENTRY_NAME));
    ModelUtil.writeModel(buildModel, zip);
    zip.closeEntry();

    zip.putNextEntry(new ZipEntry(CHECK_MODEL_ENTRY_NAME));
    ModelUtil.writeModel(checkModel, zip);
    zip.closeEntry();

    zip.putNextEntry(new ZipEntry(PARSER_TAGGER_MODEL_ENTRY_NAME));
    getParserTaggerModel().serialize(zip);
    zip.closeEntry();

    zip.putNextEntry(new ZipEntry(CHUNKER_TAGGER_MODEL_ENTRY_NAME));
    getParserChunkerModel().serialize(zip);
    zip.closeEntry();

    zip.putNextEntry(new ZipEntry(HEAD_RULES_MODEL_ENTRY_NAME));
    headRules.serialize(new OutputStreamWriter(zip, "UTF-8"));
    zip.closeEntry();
  }

  public static ParserModel create(InputStream in) throws IOException, InvalidFormatException {

    ZipInputStream zip = new ZipInputStream(in);

    AbstractModel buildModel = null;
    AbstractModel checkModel = null;

    POSModel parserTagger = null;
    ChunkerModel parserChunker = null;

    opennlp.tools.parser.lang.en.HeadRules headRules = null;

    ZipEntry entry;
    while((entry = zip.getNextEntry()) != null) {
      if (BUILD_MODEL_ENTRY_NAME.equals(entry.getName())) {

        buildModel = new BinaryGISModelReader(
            new DataInputStream(zip)).getModel();

        zip.closeEntry();
      }
      else if (CHECK_MODEL_ENTRY_NAME.equals(entry.getName())) {

        checkModel = new BinaryGISModelReader(
            new DataInputStream(zip)).getModel();

        zip.closeEntry();
      }
      else if (PARSER_TAGGER_MODEL_ENTRY_NAME.equals(entry.getName())) {

        parserTagger = new POSModel(zip);
        zip.closeEntry();
      }
      else if (CHUNKER_TAGGER_MODEL_ENTRY_NAME.equals(entry.getName())) {

        parserChunker = new ChunkerModel(zip);
        zip.closeEntry();
      }
      else if (HEAD_RULES_MODEL_ENTRY_NAME.equals(entry.getName())) {

        headRules = new opennlp.tools.parser.lang.en.HeadRules(new BufferedReader
            (new InputStreamReader(zip, "UTF-8")));

        zip.closeEntry();
      }
      else {
        throw new InvalidFormatException("Model contains unkown resource!");
      }
    }

    // TODO: add checks, everything must be =! null

    return new ParserModel(buildModel, checkModel, parserTagger, parserChunker, headRules);
  }

  private static AbstractModel readModel(String fileName) throws FileNotFoundException, IOException {
    return new GenericModelReader(new BinaryFileDataReader(new FileInputStream(fileName))).
        getModel();
  }

  public static void main(String[] args) throws FileNotFoundException, IOException, InvalidFormatException {
    if (args.length != 6){
      System.err.println("ParserModel packageName buildModel checkModel headRules chunkerModel posModel");
      System.exit(1);
    }

    AbstractModel buildModel = readModel(args[1]);

    AbstractModel checkModel = readModel(args[2]);

    opennlp.tools.parser.lang.en.HeadRules headRules =
        new opennlp.tools.parser.lang.en.HeadRules(args[3]);

    ChunkerModel chunkerModel = new ChunkerModel(new FileInputStream(args[4]));

    POSModel posModel = new POSModel(new FileInputStream(args[5]));

    ParserModel packageModel = new ParserModel(buildModel, checkModel, posModel,
        chunkerModel, headRules);

    packageModel.serialize(new FileOutputStream(args[0]));
  }
}