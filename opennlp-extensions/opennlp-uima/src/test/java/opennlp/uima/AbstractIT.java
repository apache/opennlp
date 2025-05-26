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

package opennlp.uima;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.FloatArrayFS;
import org.apache.uima.cas.IntArrayFS;
import org.apache.uima.cas.StringArrayFS;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.jcas.tcas.Annotation;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.models.ModelType;
import opennlp.tools.postag.POSModel;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.DownloadUtil;

abstract class AbstractIT extends AbstractUimaTest {

  private static final Logger logger = LoggerFactory.getLogger(AbstractIT.class);

  protected static final String BIN = ".bin";

  private static final String BASE_URL_MODELS_V15 = "https://opennlp.sourceforge.net/models-1.5/";

  @BeforeAll
  public static void initEnv() throws IOException {
    // ensure referenced UD models are present in download home
    DownloadUtil.downloadModel("en", ModelType.TOKENIZER, TokenizerModel.class);
    DownloadUtil.downloadModel("en", ModelType.SENTENCE_DETECTOR, SentenceModel.class);
    DownloadUtil.downloadModel("en", ModelType.POS, POSModel.class);

    // ensure referenced classic model files are present in download home
    for (String modelName: List.of("en-ner-organization", "en-ner-location", "en-ner-person",
            "en-ner-date", "en-ner-time", "en-ner-percentage", "en-ner-money",
            "en-chunker", "en-parser-chunking")) {
      downloadVersion15Model(modelName + BIN);
    }
  }

  private static void downloadVersion15Model(String modelName) throws IOException {
    downloadModel(new URL(BASE_URL_MODELS_V15 + modelName));
  }

  private static void downloadModel(URL url) throws IOException {
    if (!Files.isDirectory(OPENNLP_DIR)) {
      OPENNLP_DIR.toFile().mkdir();
    }
    final String filename = url.toString().substring(url.toString().lastIndexOf("/") + 1);
    final Path localFile = Paths.get(OPENNLP_DIR.toString(), filename);

    if (!Files.exists(localFile)) {
      logger.debug("Downloading model from {} to {}.", url, localFile);
      try (final InputStream in = new BufferedInputStream(url.openStream())) {
        Files.copy(in, localFile, StandardCopyOption.REPLACE_EXISTING);
      }
      logger.debug("Download complete.");
    }
  }

  /**
   * Prints all Annotations to a PrintStream.
   *
   * @param aCAS
   *          the CAS containing the FeatureStructures to print
   * @param aOut
   *          the PrintStream to which output will be written
   */
  public static void printAnnotations(CAS aCAS, PrintStream aOut) {

    // Version 3 using select with Stream support
    aCAS.select(Annotation.class).forEach(fs -> printFS(fs, aCAS, 0, aOut));
  }

  /**
   * Prints a FeatureStructure to a PrintStream.
   *
   * @param aFS
   *          the FeatureStructure to print
   * @param aCAS
   *          the CAS containing the FeatureStructure
   * @param aNestingLevel
   *          number of tabs to print before each line
   * @param aOut
   *          the PrintStream to which output will be written
   */
  public static void printFS(FeatureStructure aFS, CAS aCAS, int aNestingLevel, PrintStream aOut) {
    Type stringType = aCAS.getTypeSystem().getType(CAS.TYPE_NAME_STRING);

    printTabs(aNestingLevel, aOut);
    aOut.println(aFS.getType().getName());

    // if it's an annotation, print the first 64 chars of its covered text
    if (aFS instanceof AnnotationFS annot) {
      String coveredText = annot.getCoveredText();
      printTabs(aNestingLevel + 1, aOut);
      aOut.print("\"");
      if (coveredText.length() <= 64) {
        aOut.print(coveredText);
      } else {
        aOut.println(coveredText.substring(0, 64) + "...");
      }
      aOut.println("\"");
    }

    // print all features
    List<Feature> aFeatures = aFS.getType().getFeatures();
    for (Feature feat : aFeatures) {
      printTabs(aNestingLevel + 1, aOut);
      // print feature name
      aOut.print(feat.getShortName());
      aOut.print(" = ");
      // prnt feature value (how we get this depends on feature's range type)
      String rangeTypeName = feat.getRange().getName();
      if (aCAS.getTypeSystem().subsumes(stringType, feat.getRange())) // must check for subtypes of
      // string
      {
        String str = aFS.getStringValue(feat);
        if (str == null) {
          aOut.println("null");
        } else {
          aOut.print("\"");
          if (str.length() > 64) {
            str = str.substring(0, 64) + "...";
          }
          aOut.print(str);
          aOut.println("\"");
        }
      } else if (CAS.TYPE_NAME_INTEGER.equals(rangeTypeName)) {
        aOut.println(aFS.getIntValue(feat));
      } else if (CAS.TYPE_NAME_FLOAT.equals(rangeTypeName)) {
        aOut.println(aFS.getFloatValue(feat));
      } else if (CAS.TYPE_NAME_STRING_ARRAY.equals(rangeTypeName)) {
        StringArrayFS arrayFS = (StringArrayFS) aFS.getFeatureValue(feat);
        if (arrayFS == null) {
          aOut.println("null");
        } else {
          String[] vals = arrayFS.toArray();
          aOut.print("[");
          for (int i = 0; i < vals.length - 1; i++) {
            aOut.print(vals[i]);
            aOut.print(',');
          }
          if (vals.length > 0) {
            aOut.print(vals[vals.length - 1]);
          }
          aOut.println("]\"");
        }
      } else if (CAS.TYPE_NAME_INTEGER_ARRAY.equals(rangeTypeName)) {
        IntArrayFS arrayFS = (IntArrayFS) aFS.getFeatureValue(feat);
        if (arrayFS == null) {
          aOut.println("null");
        } else {
          int[] vals = arrayFS.toArray();
          aOut.print("[");
          for (int i = 0; i < vals.length - 1; i++) {
            aOut.print(vals[i]);
            aOut.print(',');
          }
          if (vals.length > 0) {
            aOut.print(vals[vals.length - 1]);
          }
          aOut.println("]\"");
        }
      } else if (CAS.TYPE_NAME_FLOAT_ARRAY.equals(rangeTypeName)) {
        FloatArrayFS arrayFS = (FloatArrayFS) aFS.getFeatureValue(feat);
        if (arrayFS == null) {
          aOut.println("null");
        } else {
          float[] vals = arrayFS.toArray();
          aOut.print("[");
          for (int i = 0; i < vals.length - 1; i++) {
            aOut.print(vals[i]);
            aOut.print(',');
          }
          if (vals.length > 0) {
            aOut.print(vals[vals.length - 1]);
          }
          aOut.println("]\"");
        }
      } else // non-primitive type
      {
        FeatureStructure val = aFS.getFeatureValue(feat);
        if (val == null) {
          aOut.println("null");
        } else {
          printFS(val, aCAS, aNestingLevel + 1, aOut);
        }
      }
    }
  }

  /**
   * Prints tabs to a PrintStream.
   *
   * @param aNumTabs
   *          number of tabs to print
   * @param aOut
   *          the PrintStream to which output will be written
   */
  private static void printTabs(int aNumTabs, PrintStream aOut) {
    for (int i = 0; i < aNumTabs; i++) {
      aOut.print("\t");
    }
  }
}
