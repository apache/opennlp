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

package opennlp.tools.namefind;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import opennlp.maxent.DataStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamException;
import opennlp.tools.util.Span;

/**
 * The {@link NameSampleDataStream} class converts tagged {@link String}s
 * provided by a {@link DataStream} to {@link NameSample} objects.
 * It uses text that is is one-sentence per line and tokenized
 * with names identified by <code>&lt;START&gt;</code> and <code>&lt;END&gt;</code> tags.
 */
public class NameSampleDataStream implements ObjectStream<NameSample> {

  // pattern to match the start/end tags with optional nameType.
  private Pattern startTagPattern = Pattern.compile("<START(:(\\w*))?>");

  public static final String START_TAG_PREFIX = "<START:";
  public static final String START_TAG = "<START>";
  public static final String END_TAG = "<END>";


  private final ObjectStream<String> in;

  public NameSampleDataStream(ObjectStream<String> in) {
    this.in = in;
  }

  public NameSample read() throws ObjectStreamException {
      String token = in.read();
      
      if (token != null) {
        // TODO: clear adaptive data for every empty line
        return createNameSample(token);
      }
      else {
        return null;
      }
  }

  public void reset() throws ObjectStreamException,
      UnsupportedOperationException {
    throw new UnsupportedOperationException();
  }
  
  public void close() throws ObjectStreamException {
    in.close();
  }
  
  private NameSample createNameSample(String taggedTokens) throws ObjectStreamException {
    String[] parts = taggedTokens.split(" ");

    List<String> tokenList = new ArrayList<String>(parts.length);
    List<Span> nameList = new ArrayList<Span>();
    List<String> nameTypeList = new ArrayList<String>();

    String nameType = "";
    int startIndex = -1;
    int wordIndex = 0;
    
    // we check if at least one name has the a type. If no one has, we will
    // leave the NameType property of NameSample null.
    boolean gotAtLeastOneNameType = false;
    boolean catchingName = false;
    
    for (int pi = 0; pi < parts.length; pi++) {
      Matcher startMatcher = this.startTagPattern.matcher(parts[pi]);
      if (startMatcher.matches()) {
        if(catchingName) {
          throw new ObjectStreamException("Found unexpected annotation " + parts[pi] + " while handling a name sequence.");
        }
        catchingName = true;
        startIndex = wordIndex;
        nameType = startMatcher.group(2);
        if(nameType != null && nameType.length() == 0) {
          throw new ObjectStreamException("Missing a name type: " + parts[pi]);
        }
          
      }
      else if (parts[pi].equals(END_TAG)) {
        if(catchingName == false) {
          throw new ObjectStreamException("Found unexpected annotation " + parts[pi] + ".");
        }
        catchingName = false;
        // create name
        nameList.add(new Span(startIndex, wordIndex));
        
        // always set a nameType, but we will only add it to the NameSample if at 
        // least we got one not null
        nameTypeList.add(nameType);
        if(nameType != null)
        {
          gotAtLeastOneNameType = true;
        }
        
      }
      else {
        tokenList.add(parts[pi]);
        wordIndex++;
      }
    }
    String[] sentence = tokenList.toArray(new String[tokenList.size()]);
    Span[] names = nameList.toArray(new Span[nameList.size()]);
    if(gotAtLeastOneNameType) {
      String[] nameTypes = nameTypeList.toArray(new String[nameTypeList.size()]);
      return new NameSample(sentence, names, nameTypes, sentence.length==0 );
    }
    else {
      return new NameSample(sentence, names, sentence.length==0 );
    }    
   
  }
}
