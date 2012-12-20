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

package opennlp.tools.formats;

import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.BasicFormatParams;
import opennlp.tools.postag.POSSample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

/**
 * <b>Note:</b> Do not use this class, internal use only!
 */
public class ConllXPOSSampleStreamFactory extends AbstractSampleStreamFactory<POSSample> {

  public static final String CONLLX_FORMAT = "conllx";
  
  interface Parameters extends BasicFormatParams {
  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(POSSample.class,
        CONLLX_FORMAT, new ConllXPOSSampleStreamFactory(Parameters.class));
  }

  protected <P> ConllXPOSSampleStreamFactory(Class<P> params) {
    super(params);
  }

  public ObjectStream<POSSample> create(String[] args) {
    Parameters params = ArgumentParser.parse(args, Parameters.class);

    ObjectStream<String> lineStream;
    try {
      lineStream = new PlainTextByLineStream(new InputStreamReader(
          CmdLineUtil.openInFile(params.getData()), "UTF-8"));
      System.setOut(new PrintStream(System.out, true, "UTF-8"));

      return new ConllXPOSSampleStream(lineStream);
    } catch (UnsupportedEncodingException e) {
      // this shouldn't happen
      throw new TerminateToolException(-1, "UTF-8 encoding is not supported: " + e.getMessage(), e);
    }
  }
}
