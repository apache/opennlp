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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.DetokenizerParameter;
import opennlp.tools.tokenize.DetokenizationDictionary;
import opennlp.tools.tokenize.Detokenizer;
import opennlp.tools.tokenize.DictionaryDetokenizer;

/**
 * Base class for factories which need detokenizer.
 */
public abstract class DetokenizerSampleStreamFactory<T> extends AbstractSampleStreamFactory<T> {

  protected <P> DetokenizerSampleStreamFactory(Class<P> params) {
    super(params);
  }

  protected Detokenizer createDetokenizer(DetokenizerParameter p) {
    try {
      return new DictionaryDetokenizer(new DetokenizationDictionary(
          new FileInputStream(new File(p.getDetokenizer()))));
    } catch (IOException e) {
      throw new TerminateToolException(-1, "IO error while loading detokenizer dict: " + e.getMessage(), e);
    }
  }
}