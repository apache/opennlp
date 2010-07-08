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

package opennlp.tools.cmdline.chunker;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.chunker.DefaultChunkerSequenceValidator;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.postag.POSSample;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamException;
import opennlp.tools.util.ParseException;
import opennlp.tools.util.PlainTextByLineStream;

public class ChunkerMETool implements CmdLineTool {

  public String getName() {
    return "ChunkerME";
  }
  
  public String getShortDescription() {
    return "learnable chunker";
  }
  
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " model < sentences";
  }

  static ChunkerModel loadModel(File modelFile) {
    
    CmdLineUtil.checkInputFile("Chunker model", modelFile);

    System.err.print("Loading model ... ");
    
    InputStream modelIn = CmdLineUtil.openInFile(modelFile);
    
    ChunkerModel model;
    try {
      model = new ChunkerModel(modelIn);
      modelIn.close();
    }
    catch (IOException e) {
      System.err.println("failed");
      System.err.println("IO error while loading model: " + e.getMessage());
      System.exit(-1);
      return null;
    }
    catch (InvalidFormatException e) {
      System.err.println("failed");
      System.err.println("Model has invalid format: " + e.getMessage());
      System.exit(-1);
      return null;
    }
    
    System.err.println("done");
    
    return model;
  }

  
  public void run(String[] args) {
    if (args.length != 1) {
      System.out.println(getHelp());
      System.exit(1);
    }
    
    ChunkerModel model = loadModel(new File(args[0]));
    
    ChunkerME chunker = new ChunkerME(model, ChunkerME.DEFAULT_BEAM_SIZE,
        new DefaultChunkerSequenceValidator());
    
    ObjectStream<String> lineStream =
      new PlainTextByLineStream(new InputStreamReader(System.in));
    
    try {
      String line;
      while ((line = lineStream.read()) != null) {
        
        POSSample posSample;
        try {
          posSample = POSSample.parse(line);
        } catch (ParseException e) {
          System.err.println("Invalid format:");
          System.err.println(line);
          continue;
        }
        
        String[] chunks = chunker.chunk(posSample.getSentence(),
            posSample.getTags());
        
        for (int ci=0,cn=chunks.length;ci<cn;ci++) {
          if (ci > 0 && !chunks[ci].startsWith("I-") && !chunks[ci-1].equals("O")) {
            System.out.print(" ]");
          }
          if (chunks[ci].startsWith("B-")) {
            System.out.print(" ["+chunks[ci].substring(2));
          }

          System.out.print(" "+posSample.getSentence()[ci]+"_"+posSample.getTags()[ci]);
        }
        
        if (chunks.length > 0 && !chunks[chunks.length-1].equals("O")) {
          System.out.print(" ]");
        }
        
        System.out.println();
      }
    } 
    catch (ObjectStreamException e) {
      CmdLineUtil.handleStdinIoError(e);
    } 
  }
}
