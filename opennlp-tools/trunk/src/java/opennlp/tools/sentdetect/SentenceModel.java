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


package opennlp.tools.sentdetect;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import opennlp.maxent.GISModel;
import opennlp.maxent.io.BinaryGISModelReader;
import opennlp.model.AbstractModel;
import opennlp.model.MaxentModel;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ModelUtil;
import opennlp.tools.util.StringList;

/**
 * The {@link SentenceModel} is the model used
 * by a learnable {@link SentenceDetector}.
 * 
 * TODO: read and write all parts of the model!
 * 
 * @see SentenceDetectorME
 */
public class SentenceModel {

  private static final String MAXENT_MODEL_ENTRY_NAME = "sent.bin";
  private static final String ABBREVIATIONS_ENTRY_NAME = "abbreviations.xml";
  private static final String SETTINGS_ENTRY_NAME = "settings.properties";
  
  private static final String TOKEN_END_PROPERTY = "useTokenEnd";
  
  private static final String END_OF_SENTENCE_CHARS_PROPERTY = "endOfSentenceChars";
  
  private AbstractModel sentModel;
  
  private char endOfSentenceChars[];
  
  private Set<String> abbreviations;
  
  private final boolean useTokenEnd;
  
  public SentenceModel(AbstractModel sentModel, char[] endOfSentenceChars, boolean useTokenEnd, 
      Set<String> abbreviations) {
    
    if (sentModel == null)
        throw new IllegalArgumentException("sentModel param must not be null!");
    
    if (!isModelCompatible(sentModel))
        throw new IllegalArgumentException("The maxent model is not compatible!");
      
    this.sentModel = sentModel;
    
    this.endOfSentenceChars = endOfSentenceChars;
    
    this.useTokenEnd = useTokenEnd;
    
    this.abbreviations = abbreviations;
  }
  
  private static boolean isModelCompatible(MaxentModel model) {
    // TODO: add checks, what are the outcomes ?
    return true;
  }
  
  public MaxentModel getMaxentModel() {
    return sentModel;
  }
  
  public char[] getEndOfSentenceCharacters() {
    return endOfSentenceChars;
  }
  
  public Set<String> getAbbreviations() {
    return abbreviations;
  }
  
  public boolean useTokenEnd() {
    return useTokenEnd;
  }
  
  /**
   * .
   * 
   * After the serialization is finished the provided 
   * {@link OutputStream} is closed.
   * 
   * @param out
   * 
   * @throws IOException
   */
  public void serialize(OutputStream out) throws IOException {
    final ZipOutputStream zip = new ZipOutputStream(out);
    
    // write model
    zip.putNextEntry(new ZipEntry(MAXENT_MODEL_ENTRY_NAME));
    ModelUtil.writeModel(sentModel, zip);
    zip.closeEntry();
    
    // write abbreviations
    zip.putNextEntry(new ZipEntry(ABBREVIATIONS_ENTRY_NAME));
    
    Dictionary abbreviationDictionary = new Dictionary();
    
    for (String abbreviation : abbreviations) {
      abbreviationDictionary.put(new StringList(abbreviation));
    }
    
    abbreviationDictionary.serialize(zip);
    
    zip.closeEntry();
    
    // write properties
    zip.putNextEntry(new ZipEntry(SETTINGS_ENTRY_NAME));
    
    Properties settings = new Properties();
    
    settings.put(TOKEN_END_PROPERTY, Boolean.toString(useTokenEnd()));
    
    StringBuilder endOfSentenceCharString = new StringBuilder();
    
    for (char character : getEndOfSentenceCharacters()) {
      endOfSentenceCharString.append(character);
    }
      
    settings.put(END_OF_SENTENCE_CHARS_PROPERTY, endOfSentenceCharString.toString());
    
    zip.closeEntry();
    
    zip.close();
  }
  
  /**
   * Creates a {@link SentenceModel} from the provided {@link InputStream}.
   * 
   * The {@link InputStream} in remains open after the model is read.
   * 
   * @param in
   * 
   * @return
   * 
   * @throws IOException
   * @throws InvalidFormatException
   */
  public static SentenceModel create(InputStream in) throws IOException, InvalidFormatException {
    
    ZipInputStream zip = new ZipInputStream(in);
    
    AbstractModel sentModel = null;
    Properties settings = null;
    
    Set<String> abbreviations = null;
    
    ZipEntry entry;
    while((entry = zip.getNextEntry()) != null ) {
      if (MAXENT_MODEL_ENTRY_NAME.equals(entry.getName())) {
        
        // read model
        sentModel = new BinaryGISModelReader(
            new DataInputStream(zip)).getModel();
        
        zip.closeEntry();
      }
      else if (SETTINGS_ENTRY_NAME.equals(entry.getName())) {
        
        // read properties
        settings = new Properties();
        settings.load(zip);
        
        zip.closeEntry();
      }
      else if (ABBREVIATIONS_ENTRY_NAME.equals(entry.getName())) {
        Dictionary abbreviationDictionary = new Dictionary(zip);
        
        abbreviations = new HashSet<String>();
        
        for (StringList abbreviation : abbreviationDictionary) {
          if (abbreviation.size() != 1) 
            throw new InvalidFormatException("Each abbreviation must be exactly one token!");
          
          abbreviations.add(abbreviation.getToken(0));
        }
        
        zip.closeEntry();
      }
      else {
        throw new InvalidFormatException("Model contains unkown resource!");
      }
    }
    
    if (sentModel == null)
      throw new InvalidFormatException("Unable to find " + MAXENT_MODEL_ENTRY_NAME + " maxent model!");
    
    if (settings == null)
      throw new InvalidFormatException("Unable to find " + SETTINGS_ENTRY_NAME + " !");
    
    String useTokenEndString = settings.getProperty(TOKEN_END_PROPERTY);
    
    if (useTokenEndString == null)
      throw new InvalidFormatException(TOKEN_END_PROPERTY + " is a mandatory property!");
    
    boolean useTokenEnd = Boolean.parseBoolean(useTokenEndString);
    
    String endOfSentenceCharsString = settings.getProperty(END_OF_SENTENCE_CHARS_PROPERTY);
    
    if (endOfSentenceCharsString == null)
      throw new InvalidFormatException(END_OF_SENTENCE_CHARS_PROPERTY + " is a mandatory property!");
    
    if (abbreviations == null)
      abbreviations = Collections.emptySet();
    
    return new SentenceModel(sentModel, endOfSentenceCharsString.toCharArray(), 
        useTokenEnd, abbreviations);
  }
}