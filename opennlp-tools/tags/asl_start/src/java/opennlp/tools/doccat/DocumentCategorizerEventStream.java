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


package opennlp.tools.doccat;

import opennlp.maxent.DataStream;
import opennlp.model.Event;
import opennlp.model.EventStream;

/**
* Iterator-like class for modeling document classification events.
*/
public class DocumentCategorizerEventStream implements EventStream {
  
  private DocumentCategorizerContextGenerator mContextGenerator;
  
  private DataStream data;
  
  /**
   * Initializes the current instance.
   * 
   * @param data {@link opennlp.maxent.DataStream} of {@link DocumentSample}s
   * 
   * @param featureGenerators
   */
  public DocumentCategorizerEventStream(DataStream data, FeatureGenerator featureGenerators[]) {
    
    this.data = data;
    
    mContextGenerator = 
      new DocumentCategorizerContextGenerator(featureGenerators);
  }
  
  /**
   * Initializes the current instance.
   * 
   * @param data {@link DataStream} of {@link DocumentSample}s
   */
  public DocumentCategorizerEventStream(DataStream data) {
	  this(data, new FeatureGenerator[]{new BagOfWordsFeatureGenerator()});
  }
  
  public boolean hasNext() {
    return data.hasNext();
  }

  public Event nextEvent() {
	  
    DocumentSample sample = (DocumentSample) data.nextToken();
    
    return new Event(sample.getCategory(), 
        mContextGenerator.getContext(sample.getText()));
  }
}