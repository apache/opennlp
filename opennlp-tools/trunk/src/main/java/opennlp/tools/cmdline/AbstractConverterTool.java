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

package opennlp.tools.cmdline;

import java.io.IOException;

import opennlp.tools.util.ObjectStream;

public abstract class AbstractConverterTool<T> implements CmdLineTool {
  
  private String createHelpString(String format, String usage) {
    return "Usage: " + CLI.CMD + " " + getName() + " " + format + " " + usage;
  }
  
  public String getHelp() {
    return createHelpString("format", "...");
  }
  
  protected abstract ObjectStreamFactory<T> createStreamFactory(String format);
  
  public void run(String[] args) {
    
    String format = null;
    if (args.length > 0) {
      format = args[0];
    }
    else {
      System.out.println(getHelp());
      throw new TerminateToolException(1);
    }
    
    ObjectStreamFactory<T> streamFactory = createStreamFactory(format);
    
    if (streamFactory == null) {
      // TODO: print list of available formats
      System.err.println("Format is unkown: " + format);
      throw new TerminateToolException(-1);
    }
    
    String formatArgs[] = new String[args.length - 1];
    System.arraycopy(args, 1, formatArgs, 0, formatArgs.length);
    
    if (!streamFactory.validateArguments(formatArgs)) {
      System.err.println(createHelpString(format, streamFactory.getUsage()));
      throw new TerminateToolException(-1);
    }
    
    ObjectStream<T> sampleStream = streamFactory.create(formatArgs);
    
    try {
      Object sample;
      while((sample = sampleStream.read()) != null) {
        System.out.println(sample.toString());
      }
    }
    catch (IOException e) {
      CmdLineUtil.printTrainingIoError(e);
      throw new TerminateToolException(-1);
    }
    finally {
      if (sampleStream != null)
        try {
          sampleStream.close();
        } catch (IOException e) {
          // sorry that this can fail
        }
    }
  }
}
