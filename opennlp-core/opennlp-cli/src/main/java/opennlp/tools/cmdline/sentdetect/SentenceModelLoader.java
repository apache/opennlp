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

package opennlp.tools.cmdline.sentdetect;

import java.io.IOException;
import java.io.InputStream;

import opennlp.tools.cmdline.ModelLoader;
import opennlp.tools.commons.Internal;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.util.InvalidFormatException;

/**
 * Loads a {@link SentenceModel} for the command line tools.
 * <p>
 * <b>Note:</b> Do not use this class, internal use only!
 */
@Internal
final class SentenceModelLoader extends ModelLoader<SentenceModel> {

  public SentenceModelLoader() {
    super("Sentence Detector");
  }

  @Override
  protected SentenceModel loadModel(InputStream modelIn) throws IOException,
      InvalidFormatException {
    return new SentenceModel(modelIn);
  }

}
