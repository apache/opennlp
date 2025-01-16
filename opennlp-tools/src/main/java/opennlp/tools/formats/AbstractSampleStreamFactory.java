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

import java.io.IOException;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.ObjectStreamFactory;
import opennlp.tools.cmdline.params.BasicFormatParams;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

/**
 * Base class for sample stream factories.
 */
public abstract class AbstractSampleStreamFactory<T,P> implements ObjectStreamFactory<T,P> {

  protected Class<P> params;

  private AbstractSampleStreamFactory() {
  }

  protected AbstractSampleStreamFactory(Class<P> params) {
    this.params = params;
  }

  public String getLang() {
    return "eng";
  }

  public Class<P> getParameters() {
    return params;
  }

  /**
   * Creates an {@link ObjectStream} for the specified arguments and
   * the generic type {@code P}.
   *
   * @param args A set of command line arguments.
   * @return The created {@link ObjectStream} instance.
   */
  protected <P extends BasicFormatParams> ObjectStream<String> readData(String[] args,
                                                                        Class<P> parametersClass) {
    P params = validateBasicFormatParameters(args, parametersClass);
    ObjectStream<String> lineStream = null;
    try {
      InputStreamFactory sampleDataIn = CmdLineUtil.createInputStreamFactory(params.getData());
      lineStream = new PlainTextByLineStream(sampleDataIn, params.getEncoding());
    } catch (IOException ex) {
      CmdLineUtil.handleCreateObjectStreamError(ex);
    }
    return lineStream;
  }

  /**
   * Validates the specified arguments ({@code args}) given the
   * context the generic type {@code P} which provides at least all
   * {@link BasicFormatParams}.
   *
   * @implNote Additional checks for the basic {@code -data} argument are conducted, that is
   * wether the file exists or not.
   *
   * @param args A set of command line arguments.
   * @return The parsed (basic format) parameter instance.
   */
  protected <P extends BasicFormatParams> P validateBasicFormatParameters(String[] args, Class<P> clazz) {
    if (args == null) {
      throw new IllegalArgumentException("Passed args must not be null!");
    }
    P params = ArgumentParser.parse(args, clazz);
    CmdLineUtil.checkInputFile("Data", params.getData());
    return params;
  }
}
