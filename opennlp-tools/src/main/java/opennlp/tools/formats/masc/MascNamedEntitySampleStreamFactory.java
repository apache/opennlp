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

package opennlp.tools.formats.masc;

import java.io.FileFilter;
import java.io.IOException;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.params.BasicFormatParams;
import opennlp.tools.formats.AbstractSampleStreamFactory;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.ObjectStream;

public class MascNamedEntitySampleStreamFactory<P> extends AbstractSampleStreamFactory<NameSample, P> {
  public static final String MASC_FORMAT = "masc";

  protected MascNamedEntitySampleStreamFactory(Class<P> params) {
    super(params);
  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(NameSample.class,
        MASC_FORMAT,
        new MascNamedEntitySampleStreamFactory<>(
            MascNamedEntitySampleStreamFactory.Parameters.class));
  }

  @Override
  public ObjectStream<NameSample> create(String[] args) {
    MascNamedEntitySampleStreamFactory.Parameters params =
        ArgumentParser.parse(args, MascNamedEntitySampleStreamFactory.Parameters.class);

    try {
      FileFilter fileFilter = pathname -> pathname.getName().contains(params.getFileFilter());

      return new MascNamedEntitySampleStream(
          new MascDocumentStream(params.getData(), params.getRecurrentSearch(), fileFilter));
    } catch (IOException e) {
      CmdLineUtil.handleCreateObjectStreamError(e);
    }
    return null;
  }

  interface Parameters extends BasicFormatParams {

    @ArgumentParser.ParameterDescription(valueName = "recurrentSearch",
        description = "search through files recursively")
    boolean getRecurrentSearch();

    @ArgumentParser.ParameterDescription(valueName = "fileFilterString",
        description = "only include files which contain a given string in their name")
    String getFileFilter();

  }

}
