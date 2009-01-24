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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import opennlp.model.Event;
import opennlp.model.EventCollector;
import opennlp.tools.util.Pair;

/**
 * An event generator for the maxent POS Tagger.
 *
 * @author      Gann Bierner
 * @version     $Revision: 1.1 $, $Date: 2009-01-24 00:22:48 $
 */
@Deprecated
public class POSEventCollector implements EventCollector {
  
  private BufferedReader br;
  private POSContextGenerator cg;
  
  /**
   * Initializes the current instance.
   * 
   * @param data
   * @param gen
   */
  public POSEventCollector(Reader data, POSContextGenerator gen) {
    br = new BufferedReader(data);
    cg = gen;
  }
  
  private static Pair<String, String> split(String s) {
    int split = s.lastIndexOf("_");
    if (split == -1) {
      System.out.println("There is a problem in your training data: "
          + s
          + " does not conform to the format WORD_TAG.");
      return new Pair<String, String>(s, "UNKNOWN");
    }
    
    return new Pair<String, String>(s.substring(0, split), s.substring(split+1));
  }
  
  public static Pair<List<String>, List<String>> convertAnnotatedString(String s) {
    ArrayList<String> tokens = new ArrayList<String>();
    ArrayList<String> outcomes = new ArrayList<String>();
    StringTokenizer st = new StringTokenizer(s);
    while(st.hasMoreTokens()) {
      Pair<String, String> p = split(st.nextToken());
      tokens.add(p.a);
      outcomes.add(p.b);
    }
    return new Pair<List<String>, List<String>>(tokens, outcomes);
  }
  
  public Event[] getEvents() {
    return getEvents(false);
  }
    
  /** 
   * Builds up the list of features using the Reader as input.  For now, this
   * should only be used to create training data.
   */
  public Event[] getEvents(boolean evalMode) {
    List<Event> elist = new ArrayList<Event>();
    try {
      String s = br.readLine();
      
      while (s != null) {
        Pair<List<String>, List<String>> p = convertAnnotatedString(s);
        List<String> tokens = p.a;
        List<String> outcomes = p.b;
        List<String> tags = new ArrayList<String>();
        
        for (int i=0; i<tokens.size(); i++) {
          String[] context = cg.getContext(i,tokens.toArray(new String[tokens.size()]),(String[]) tags.toArray(new String[tags.size()]),null);
          Event e = new Event((String)outcomes.get(i), context);
          tags.add(outcomes.get(i));
          elist.add(e);
        }
        s = br.readLine();
      }
    } 
    catch (IOException e) { 
      e.printStackTrace(); 
    }
    
    Event[] events = new Event[elist.size()];
    for(int i=0; i<events.length; i++)
      events[i] = (Event)elist.get(i);
    
    return events;
  }
}