/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package opennlp.maxent;

import opennlp.model.AbstractEventStream;
import opennlp.model.Event;
import opennlp.model.EventStream;
import opennlp.model.RealValueFileEventStream;

public class RealBasicEventStream extends  AbstractEventStream {
  ContextGenerator cg = new BasicContextGenerator();
  DataStream ds;
  Event next;
  
  public RealBasicEventStream(DataStream ds) {
    this.ds = ds;
    if (this.ds.hasNext())
      next = createEvent((String)this.ds.nextToken());
    
  }

  public Event next() {
    while (next == null && this.ds.hasNext())
      next = createEvent((String)this.ds.nextToken());
    
    Event current = next;
    if (this.ds.hasNext()) {
      next = createEvent((String)this.ds.nextToken());
    }
    else {
      next = null;
    }
    return current;
  }

  public boolean hasNext() {
    while (next == null && ds.hasNext())
      next = createEvent((String)ds.nextToken());
    return next != null;
  }
  
  private Event createEvent(String obs) {
    int lastSpace = obs.lastIndexOf(' ');
    if (lastSpace == -1) 
      return null;
    else {
      String[] contexts = obs.substring(0,lastSpace).split("\\s+");
      float[] values = RealValueFileEventStream.parseContexts(contexts);
      return new Event(obs.substring(lastSpace+1),contexts,values);
    }
  }

  public static void main(String[] args) throws java.io.IOException {
    EventStream es = new RealBasicEventStream(new PlainTextByLineDataStream(new java.io.FileReader(args[0])));
    while (es.hasNext()) {
      System.out.println(es.next());
    }
  }
}
