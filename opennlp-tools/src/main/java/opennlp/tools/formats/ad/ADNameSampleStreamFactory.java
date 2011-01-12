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

package opennlp.tools.formats.ad;

import java.io.File;
import java.nio.charset.Charset;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.ObjectStreamFactory;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.ObjectStream;

/**
 * A Factory to create a Arvores Deitadas NameSampleStream from the command line
 * utility.
 * <p>
 * <b>Note:</b> Do not use this class, internal use only!
 */
public class ADNameSampleStreamFactory implements
    ObjectStreamFactory<NameSample> {

  interface Parameters {
    @ParameterDescription(valueName = "encoding")
    String getEncoding();

    @ParameterDescription(valueName = "sampleData")
    String getData();
  }

  public String getUsage() {
    return ArgumentParser.createUsage(Parameters.class);
  }

  public boolean validateArguments(String[] args) {
    return ArgumentParser.validateArguments(args, Parameters.class);
  }

  public ObjectStream<NameSample> create(String[] args) {

    Parameters params = ArgumentParser.parse(args, Parameters.class);

    Charset encoding = CmdLineUtil.getEncodingParameter(args);

    if (encoding == null) {
      throw new TerminateToolException(1);
    }

    return new ADNameSampleStream(CmdLineUtil.openInFile(new File(params
        .getData())), encoding.name());
  }
}
