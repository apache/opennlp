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

package opennlp.tools.cmdline.namefind;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import opennlp.tools.cmdline.AbstractTrainerTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.namefind.TokenNameFinderTrainerTool.TrainerToolParams;
import opennlp.tools.cmdline.params.TrainingToolParams;
import opennlp.tools.namefind.BilouCodec;
import opennlp.tools.namefind.BioCodec;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.NameSampleTypeFilter;
import opennlp.tools.namefind.TokenNameFinderFactory;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.SequenceCodec;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.featuregen.GeneratorFactory;
import opennlp.tools.util.model.ArtifactSerializer;
import opennlp.tools.util.model.ModelUtil;

public final class TokenNameFinderTrainerTool
    extends AbstractTrainerTool<NameSample, TrainerToolParams> {

  interface TrainerToolParams extends TrainingParams, TrainingToolParams {

  }

  public TokenNameFinderTrainerTool() {
    super(NameSample.class, TrainerToolParams.class);
  }

  public String getShortDescription() {
    return "trainer for the learnable name finder";
  }

  static byte[] openFeatureGeneratorBytes(String featureGenDescriptorFile) {
    if (featureGenDescriptorFile != null) {
      return openFeatureGeneratorBytes(new File(featureGenDescriptorFile));
    }
    return null;
  }

  public static byte[] openFeatureGeneratorBytes(File featureGenDescriptorFile) {
    byte[] featureGeneratorBytes = null;
    // load descriptor file into memory
    if (featureGenDescriptorFile != null) {

      try (InputStream bytesIn = CmdLineUtil.openInFile(featureGenDescriptorFile)) {
        featureGeneratorBytes = ModelUtil.read(bytesIn);
      } catch (IOException e) {
        throw new TerminateToolException(-1, "IO error while reading training data or indexing data: "
            + e.getMessage(), e);
      }
    }
    return featureGeneratorBytes;
  }

  /**
   * Load the resources, such as dictionaries, by reading the feature xml descriptor
   * and looking into the directory passed as argument.
   * @param resourcePath the directory in which the resources are to be found
   * @param featureGenDescriptor the feature xml descriptor
   * @return a map consisting of the file name of the resource and its corresponding Object
   */
  public static Map<String, Object> loadResources(File resourcePath, File featureGenDescriptor)
      throws IOException {
    Map<String, Object> resources = new HashMap<>();

    if (resourcePath != null) {
      Map<String, ArtifactSerializer> artifactSerializers = new HashMap<>();

      if (featureGenDescriptor != null) {

        try (InputStream xmlDescriptorIn = CmdLineUtil.openInFile(featureGenDescriptor)) {
          artifactSerializers.putAll(
              GeneratorFactory.extractArtifactSerializerMappings(xmlDescriptorIn));
        }
      }

      for (Map.Entry<String, ArtifactSerializer> serializerMapping : artifactSerializers.entrySet()) {
        String resourceName = serializerMapping.getKey();
        try (InputStream resourceIn = CmdLineUtil.openInFile(new File(resourcePath, resourceName))) {
          resources.put(resourceName, serializerMapping.getValue().create(resourceIn));
        }
      }
    }
    return resources;
  }

  public void run(String format, String[] args) {
    super.run(format, args);

    mlParams = CmdLineUtil.loadTrainingParameters(params.getParams(), true);
    if (mlParams == null) {
      mlParams = new TrainingParameters();
    }

    File modelOutFile = params.getModel();

    byte[] featureGeneratorBytes = openFeatureGeneratorBytes(params.getFeaturegen());

    // TODO: Support Custom resources:
    //       Must be loaded into memory, or written to tmp file until descriptor
    //       is loaded which defines parses when model is loaded

    Map<String, Object> resources;
    try {
      resources = loadResources(params.getResources(), params.getFeaturegen());
    }
    catch (IOException e) {
      throw new TerminateToolException(-1, e.getMessage(), e);
    }

    CmdLineUtil.checkOutputFile("name finder model", modelOutFile);

    if (params.getNameTypes() != null) {
      String[] nameTypes = params.getNameTypes().split(",");
      sampleStream = new NameSampleTypeFilter(nameTypes, sampleStream);
    }

    String sequenceCodecImplName = params.getSequenceCodec();

    if ("BIO".equals(sequenceCodecImplName)) {
      sequenceCodecImplName = BioCodec.class.getName();
    }
    else if ("BILOU".equals(sequenceCodecImplName)) {
      sequenceCodecImplName = BilouCodec.class.getName();
    }

    SequenceCodec<String> sequenceCodec =
        TokenNameFinderFactory.instantiateSequenceCodec(sequenceCodecImplName);

    TokenNameFinderFactory nameFinderFactory;
    try {
      nameFinderFactory = TokenNameFinderFactory.create(params.getFactory(),
          featureGeneratorBytes, resources, sequenceCodec);
    } catch (InvalidFormatException e) {
      throw new TerminateToolException(-1, e.getMessage(), e);
    }

    NameSampleCountersStream counters = new NameSampleCountersStream(sampleStream);
    sampleStream = counters;

    TokenNameFinderModel model;
    try {
      model = opennlp.tools.namefind.NameFinderME.train(
          params.getLang(), params.getType(), sampleStream, mlParams,
          nameFinderFactory);
    }
    catch (IOException e) {
      throw createTerminationIOException(e);
    }
    finally {
      try {
        sampleStream.close();
      } catch (IOException e) {
        // sorry that this can fail
      }
    }

    System.out.println();
    counters.printSummary();
    System.out.println();

    CmdLineUtil.writeModel("name finder", modelOutFile, model);

  }
}
