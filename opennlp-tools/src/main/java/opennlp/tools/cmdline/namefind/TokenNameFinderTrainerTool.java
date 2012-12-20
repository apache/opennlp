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
import opennlp.tools.cmdline.ArgumentParser.OptionalParameter;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.namefind.TokenNameFinderTrainerTool.TrainerToolParams;
import opennlp.tools.cmdline.params.TrainingToolParams;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.NameSampleTypeFilter;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.model.ArtifactSerializer;
import opennlp.tools.util.model.ModelUtil;

public final class TokenNameFinderTrainerTool
    extends AbstractTrainerTool<NameSample, TrainerToolParams> {
  
  interface TrainerToolParams extends TrainingParams, TrainingToolParams {
    @OptionalParameter
    @ParameterDescription(valueName = "types", description = "name types to use for training")
    String getNameTypes();
  }

  public TokenNameFinderTrainerTool() {
    super(NameSample.class, TrainerToolParams.class);
  }

  public String getShortDescription() {
    return "trainer for the learnable name finder";
  }
  
  static byte[] openFeatureGeneratorBytes(String featureGenDescriptorFile) {
    if(featureGenDescriptorFile != null) {
      return openFeatureGeneratorBytes(new File(featureGenDescriptorFile));
    }
    return null;
  }
  
  static byte[] openFeatureGeneratorBytes(File featureGenDescriptorFile) {
    byte featureGeneratorBytes[] = null;
    // load descriptor file into memory
    if (featureGenDescriptorFile != null) {
      InputStream bytesIn = CmdLineUtil.openInFile(featureGenDescriptorFile);

      try {
        featureGeneratorBytes = ModelUtil.read(bytesIn);
      } catch (IOException e) {
        throw new TerminateToolException(-1, "IO error while reading training data or indexing data: "
            + e.getMessage(), e);
      } finally {
        try {
          bytesIn.close();
        } catch (IOException e) {
          // sorry that this can fail
        }
      }
    }
    return featureGeneratorBytes;
  }
  
  public static Map<String, Object> loadResources(File resourcePath) {
    Map<String, Object> resources = new HashMap<String, Object>();

    if (resourcePath != null) {

      Map<String, ArtifactSerializer> artifactSerializers = TokenNameFinderModel
          .createArtifactSerializers();

      File resourceFiles[] = resourcePath.listFiles();

      // TODO: Filter files, also files with start with a dot
      for (File resourceFile : resourceFiles) {

        // TODO: Move extension extracting code to method and
        // write unit test for it

        // extract file ending
        String resourceName = resourceFile.getName();

        int lastDot = resourceName.lastIndexOf('.');

        if (lastDot == -1) {
          continue;
        }

        String ending = resourceName.substring(lastDot + 1);

        // lookup serializer from map
        ArtifactSerializer serializer = artifactSerializers.get(ending);

        // TODO: Do different? For now just ignore ....
        if (serializer == null)
          continue;

        InputStream resoruceIn = CmdLineUtil.openInFile(resourceFile);

        try {
          resources.put(resourceName, serializer.create(resoruceIn));
        } catch (InvalidFormatException e) {
          // TODO: Fix exception handling
          e.printStackTrace();
        } catch (IOException e) {
          // TODO: Fix exception handling
          e.printStackTrace();
        } finally {
          try {
            resoruceIn.close();
          } catch (IOException e) {
          }
        }
      }
    }
    return resources;
  }
  
  static Map<String, Object> loadResources(String resourceDirectory) {

    if (resourceDirectory != null) {
      File resourcePath = new File(resourceDirectory);
      return loadResources(resourcePath);
    }

    return new HashMap<String, Object>();
  }
  
  public void run(String format, String[] args) {
    super.run(format, args);

    mlParams = CmdLineUtil.loadTrainingParameters(params.getParams(), false);
    if(mlParams == null) {
      mlParams = ModelUtil.createTrainingParameters(params.getIterations(), params.getCutoff());
    }

    File modelOutFile = params.getModel();

    byte featureGeneratorBytes[] = openFeatureGeneratorBytes(params.getFeaturegen());
    

    // TODO: Support Custom resources:
    //       Must be loaded into memory, or written to tmp file until descriptor 
    //       is loaded which defines parses when model is loaded
    
    Map<String, Object> resources = loadResources(params.getResources());
        
    CmdLineUtil.checkOutputFile("name finder model", modelOutFile);

    if (params.getNameTypes() != null) {
      String nameTypes[] = params.getNameTypes().split(",");
      sampleStream = new NameSampleTypeFilter(nameTypes, sampleStream);
    }
    
    TokenNameFinderModel model;
    try {
      model = opennlp.tools.namefind.NameFinderME.train(
          params.getLang(), params.getType(), sampleStream,
          mlParams, featureGeneratorBytes, resources);
    }
    catch (IOException e) {
      throw new TerminateToolException(-1, "IO error while reading training data or indexing data: "
          + e.getMessage(), e);
    }
    finally {
      try {
        sampleStream.close();
      } catch (IOException e) {
        // sorry that this can fail
      }
    }
    
    CmdLineUtil.writeModel("name finder", modelOutFile, model);
  }
}
