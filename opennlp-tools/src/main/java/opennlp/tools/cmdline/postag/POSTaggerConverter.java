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

package opennlp.tools.cmdline.postag;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import opennlp.tools.cmdline.AbstractConverterTool;
import opennlp.tools.cmdline.ObjectStreamFactory;
import opennlp.tools.formats.ConllXPOSSampleStreamFactory;
import opennlp.tools.postag.POSSample;

public class POSTaggerConverter extends AbstractConverterTool<POSSample> {

  private static final Map<String, ObjectStreamFactory<POSSample>> streamFactories;
  
  static {
    Map<String, ObjectStreamFactory<POSSample>> mutableStreamFactories =
      new HashMap<String, ObjectStreamFactory<POSSample>>();
    
    mutableStreamFactories.put("conllx", new ConllXPOSSampleStreamFactory());
    
    streamFactories = Collections.unmodifiableMap(mutableStreamFactories);
  }
  
  public String getName() {
    return "POSTaggerConverter";
  }

  public String getShortDescription() {
    return "";
  }

  @Override
  protected ObjectStreamFactory<POSSample> createStreamFactory(String format) {
    return streamFactories.get(format);
  }

}
