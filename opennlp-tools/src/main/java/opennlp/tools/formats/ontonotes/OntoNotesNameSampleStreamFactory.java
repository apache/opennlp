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

package opennlp.tools.formats.ontonotes;

import java.io.File;
import java.nio.charset.StandardCharsets;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.formats.AbstractSampleStreamFactory;
import opennlp.tools.formats.DirectorySampleStream;
import opennlp.tools.formats.convert.FileToStringSampleStream;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.ObjectStream;

public class OntoNotesNameSampleStreamFactory extends
    AbstractSampleStreamFactory<NameSample> {

  public OntoNotesNameSampleStreamFactory() {
    super(OntoNotesFormatParameters.class);
  }

  public ObjectStream<NameSample> create(String[] args) {

    OntoNotesFormatParameters params = ArgumentParser.parse(args, OntoNotesFormatParameters.class);

    ObjectStream<File> documentStream = new DirectorySampleStream(new File(
        params.getOntoNotesDir()),
        file -> {
          if (file.isFile()) {
            return file.getName().endsWith(".name");
          }

          return file.isDirectory();
        }, true);

    return new OntoNotesNameSampleStream(
        new FileToStringSampleStream(documentStream, StandardCharsets.UTF_8));
  }

  public static void registerFactory() {
    StreamFactoryRegistry.registerFactory(NameSample.class,
        "ontonotes", new OntoNotesNameSampleStreamFactory());
  }
}
