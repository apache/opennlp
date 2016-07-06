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

package opennlp.morfologik.builder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import morfologik.stemming.DictionaryMetadata;
import morfologik.stemming.EncoderType;
import morfologik.tools.FSACompile;
import morfologik.tools.Launcher;

/**
 * Utility class to build Morfologik dictionaries from a tab separated values
 * file. The first column is the word, the second its lemma and the third a POS
 * tag. If there is no lemma information leave the second column empty.
 */
public class MorfologikDictionayBuilder {

  /**
   * Build a Morfologik binary dictionary
   *
   * @param dictInFile
   *          the 3 column TSV dictionary file
   * @param dictOutFile
   *          where to store the binary Morfologik dictionary
   * @param encoding
   *          the encoding to be used while reading and writing
   * @param separator
   *          a field separator, the default is '+'. If your tags contains '+'
   *          change to something else
   * @param encoderType
   *          the Morfologik enconder type
   * @param isUseInfixes
   *          if to compact using infixes
   * @throws Exception
   */
  public void build(File dictInFile, File dictOutFile, Charset encoding,
      String separator, EncoderType encoderType)
      throws Exception {
    Path propertiesPath = DictionaryMetadata
        .getExpectedMetadataLocation(dictOutFile.toPath()); 
    
    this.build(dictInFile, dictOutFile, propertiesPath.toFile(), encoding, separator,
        encoderType);
  }

  /**
   * Build a Morfologik binary dictionary
   *
   * @param dictInFile
   *          the 3 column TSV dictionary file
   * @param dictOutFile
   *          where to store the binary Morfologik dictionary
   * @param propertiesOutFile
   *          where to store the properties of the Morfologik dictionary
   * @param encoding
   *          the encoding to be used while reading and writing
   * @param separator
   *          a field separator, the default is '+'. If your tags contains '+'
   *          change to something else
   * @param isUsePrefixes
   *          if to compact using prefixes
   * @param isUseInfixes
   *          if to compact using infixes
   * @throws Exception
   */
  public void build(File dictInFile, File dictOutFile, File propertiesOutFile,
      Charset encoding, String separator, EncoderType encoderType) throws Exception {

    // we need to execute tab2morph followed by fsa_build

    File morph = tab2morph(dictInFile, separator, encoderType);

    fsaBuild(morph, dictOutFile);

    morph.delete();

    // now we create the properties files using the passed parameters
    createProperties(encoding, separator, encoderType,
        propertiesOutFile);
  }

  void createProperties(Charset encoding, String separator,
		  EncoderType encoderType, File propertiesFile)
      throws FileNotFoundException, IOException {

    Properties properties = new Properties();
    properties.setProperty("fsa.dict.separator", separator);
    properties.setProperty("fsa.dict.encoding", encoding.name());
    properties.setProperty("fsa.dict.encoder", encoderType.name());

    OutputStream os = new FileOutputStream(propertiesFile);
    properties.store(os, "Morfologik POS Dictionary properties");
    os.close();

  }

  private void fsaBuild(File morph, File dictOutFile) throws Exception {
    String[] params = { "-f", "cfsa2", "-i", morph.getAbsolutePath(), "-o",
        dictOutFile.getAbsolutePath() };
    FSACompile.main(params);
    // FSABuildTool.main(params);
  }

  private File tab2morph(File dictInFile, String separator,
      EncoderType encoderType) throws Exception {

    // create tab2morph parameters
    List<String> tag2morphParams = new ArrayList<String>();
    tag2morphParams.add("tab2morph");

    tag2morphParams.add("--annotation");
    tag2morphParams.add(separator);
    
    tag2morphParams.add("--e");
    tag2morphParams.add(encoderType.name());

    tag2morphParams.add("-i");
    tag2morphParams.add(dictInFile.getAbsolutePath());

    // we need a temporary file to store the intermediate output
    File tmp = File.createTempFile("tab2morph", ".txt");
    tmp.deleteOnExit();

    tag2morphParams.add("-o");
    tag2morphParams.add(tmp.getAbsolutePath());

    Launcher.main(tag2morphParams.toArray(new String[tag2morphParams.size()]));

    return tmp;
  }

}
