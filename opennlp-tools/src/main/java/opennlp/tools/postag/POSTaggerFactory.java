/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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

package opennlp.tools.postag;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.BaseToolFactory;
import opennlp.tools.util.SequenceValidator;
import opennlp.tools.util.model.ArtifactProvider;

/**
 * 
 */
public class POSTaggerFactory extends BaseToolFactory {

  protected Dictionary ngramDictionary;
  protected POSDictionary posDictionary;

  /**
   * Creates a {@link POSTaggerFactory} that provides the default implementation
   * of the resources.
   */
  public POSTaggerFactory() {
  }

  /**
   * Creates a {@link POSTaggerFactory} with an {@link ArtifactProvider} that
   * will be used to retrieve artifacts.
   * <p>
   * Sub-classes should implement a constructor with this signatures and call
   * this constructor.
   * <p>
   * This will be used to load the factory from a serialized POSModel.
   */
  public POSTaggerFactory(ArtifactProvider artifactProvider) {
    super(artifactProvider);
  }

  /**
   * Creates a {@link POSTaggerFactory}. Use this constructor to
   * programmatically create a factory.
   * 
   * @param ngramDictionary
   * @param posDictionary
   */
  public POSTaggerFactory(Dictionary ngramDictionary,
      POSDictionary posDictionary) {
    this.ngramDictionary = ngramDictionary;
    this.posDictionary = posDictionary;
  }

  public POSDictionary getPOSDictionary() {
    return this.posDictionary;
  }

  public POSContextGenerator getPOSContextGenerator() {
    return new DefaultPOSContextGenerator(0, ngramDictionary);
  }

  public SequenceValidator<String> getSequenceValidator() {
    return new DefaultPOSSequenceValidator(getPOSDictionary());
  }

}
