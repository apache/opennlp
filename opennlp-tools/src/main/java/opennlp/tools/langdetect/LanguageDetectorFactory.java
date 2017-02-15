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

package opennlp.tools.langdetect;

import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ext.ExtensionLoader;


public class LanguageDetectorFactory extends BaseToolFactory {

  public static LanguageDetectorFactory create(String subclassName)
      throws InvalidFormatException {
    if (subclassName == null) {
      // will create the default factory
      return new LanguageDetectorFactory();
    }
    try {
      LanguageDetectorFactory theFactory = ExtensionLoader.instantiateExtension(
          LanguageDetectorFactory.class, subclassName);
      theFactory.init();
      return theFactory;
    } catch (Exception e) {
      String msg = "Could not instantiate the " + subclassName
          + ". The initialization throw an exception.";
      throw new InvalidFormatException(msg, e);
    }
  }

  public void init() {
    // nothing to do
  }

  @Override
  public void validateArtifactMap() throws InvalidFormatException {
    // nothing to validate
  }
}
