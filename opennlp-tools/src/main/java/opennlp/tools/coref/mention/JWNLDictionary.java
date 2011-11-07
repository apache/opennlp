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

package opennlp.tools.coref.mention;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.didion.jwnl.JWNLException;
import net.didion.jwnl.data.Adjective;
import net.didion.jwnl.data.FileDictionaryElementFactory;
import net.didion.jwnl.data.IndexWord;
import net.didion.jwnl.data.POS;
import net.didion.jwnl.data.Pointer;
import net.didion.jwnl.data.PointerType;
import net.didion.jwnl.data.Synset;
import net.didion.jwnl.data.VerbFrame;
import net.didion.jwnl.dictionary.FileBackedDictionary;
import net.didion.jwnl.dictionary.MorphologicalProcessor;
import net.didion.jwnl.dictionary.file_manager.FileManager;
import net.didion.jwnl.dictionary.file_manager.FileManagerImpl;
import net.didion.jwnl.dictionary.morph.DefaultMorphologicalProcessor;
import net.didion.jwnl.dictionary.morph.DetachSuffixesOperation;
import net.didion.jwnl.dictionary.morph.LookupExceptionsOperation;
import net.didion.jwnl.dictionary.morph.LookupIndexWordOperation;
import net.didion.jwnl.dictionary.morph.Operation;
import net.didion.jwnl.dictionary.morph.TokenizerOperation;
import net.didion.jwnl.princeton.data.PrincetonWN17FileDictionaryElementFactory;
import net.didion.jwnl.princeton.file.PrincetonRandomAccessDictionaryFile;

/**
 * An implementation of the Dictionary interface using the JWNL library.
 */
public class JWNLDictionary implements Dictionary {

  private net.didion.jwnl.dictionary.Dictionary dict;
  private MorphologicalProcessor morphy;
  private static String[] empty = new String[0];

  public JWNLDictionary(String searchDirectory) throws IOException, JWNLException {
    PointerType.initialize();
	Adjective.initialize();
	VerbFrame.initialize();
    Map<POS, String[][]> suffixMap = new HashMap<POS, String[][]>();
    suffixMap.put(POS.NOUN,new String[][] {{"s",""},{"ses","s"},{"xes","x"},{"zes","z"},{"ches","ch"},{"shes","sh"},{"men","man"},{"ies","y"}});
    suffixMap.put(POS.VERB,new String[][] {{"s",""},{"ies","y"},{"es","e"},{"es",""},{"ed","e"},{"ed",""},{"ing","e"},{"ing",""}});
    suffixMap.put(POS.ADJECTIVE,new String[][] {{"er",""},{"est",""},{"er","e"},{"est","e"}});
    DetachSuffixesOperation tokDso = new DetachSuffixesOperation(suffixMap);
    tokDso.addDelegate(DetachSuffixesOperation.OPERATIONS,new Operation[] {new LookupIndexWordOperation(),new LookupExceptionsOperation()});
    TokenizerOperation tokOp = new TokenizerOperation(new String[] {" ","-"});
    tokOp.addDelegate(TokenizerOperation.TOKEN_OPERATIONS,new Operation[] {new LookupIndexWordOperation(),new LookupExceptionsOperation(),tokDso});
    DetachSuffixesOperation morphDso = new DetachSuffixesOperation(suffixMap);
    morphDso.addDelegate(DetachSuffixesOperation.OPERATIONS,new Operation[] {new LookupIndexWordOperation(),new LookupExceptionsOperation()});
    Operation[] operations = {new LookupExceptionsOperation(), morphDso , tokOp};
    morphy = new DefaultMorphologicalProcessor(operations);
    FileManager manager = new FileManagerImpl(searchDirectory,PrincetonRandomAccessDictionaryFile.class);
    FileDictionaryElementFactory factory = new PrincetonWN17FileDictionaryElementFactory();
    FileBackedDictionary.install(manager, morphy,factory,true);
    dict = net.didion.jwnl.dictionary.Dictionary.getInstance();
    morphy = dict.getMorphologicalProcessor();
  }

  @SuppressWarnings("unchecked")
  public String[] getLemmas(String word, String tag) {
    try {
      POS pos;
      if (tag.startsWith("N") || tag.startsWith("n")) {
        pos = POS.NOUN;
      }
      else if (tag.startsWith("N") || tag.startsWith("v")) {
        pos = POS.VERB;
      }
      else if (tag.startsWith("J") || tag.startsWith("a")) {
        pos = POS.ADJECTIVE;
      }
      else if (tag.startsWith("R") || tag.startsWith("r")) {
        pos = POS.ADVERB;
      }
      else {
        pos = POS.NOUN;
      }
      List<String> lemmas = morphy.lookupAllBaseForms(pos,word);
      return lemmas.toArray(new String[lemmas.size()]);
    }
    catch (JWNLException e) {
      e.printStackTrace();
      return null;
    }
  }

  public String getSenseKey(String lemma, String pos,int sense) {
    try {
      IndexWord iw = dict.getIndexWord(POS.NOUN,lemma);
      if (iw == null) {
        return null;
      }
      return String.valueOf(iw.getSynsetOffsets()[sense]);
    }
    catch (JWNLException e) {
      e.printStackTrace();
      return null;
    }

  }

  public int getNumSenses(String lemma, String pos) {
    try {
      IndexWord iw = dict.getIndexWord(POS.NOUN,lemma);
      if (iw == null){
        return 0;
      }
      return iw.getSenseCount();
    }
    catch (JWNLException e) {
      return 0;
    }
  }

  private void getParents(Synset synset, List<String> parents) throws JWNLException {
    Pointer[] pointers = synset.getPointers();
    for (int pi=0,pn=pointers.length;pi<pn;pi++) {
      if (pointers[pi].getType() == PointerType.HYPERNYM) {
        Synset parent = pointers[pi].getTargetSynset();
        parents.add(String.valueOf(parent.getOffset()));
        getParents(parent,parents);
      }
    }
  }

  public String[] getParentSenseKeys(String lemma, String pos, int sense) {
    //System.err.println("JWNLDictionary.getParentSenseKeys: lemma="+lemma);
    try {
      IndexWord iw = dict.getIndexWord(POS.NOUN,lemma);
      if (iw != null) {
        Synset synset = iw.getSense(sense+1);
        List<String> parents = new ArrayList<String>();
        getParents(synset,parents);
        return parents.toArray(new String[parents.size()]);
      }
      else {
        return empty;
      }
    }
    catch (JWNLException e) {
      e.printStackTrace();
      return null;
    }
  }

  public static void main(String[] args) throws IOException, JWNLException {
    String searchDir = System.getProperty("WNSEARCHDIR");
    System.err.println("searchDir="+searchDir);
    if (searchDir != null) {
      Dictionary dict = new JWNLDictionary(System.getProperty("WNSEARCHDIR"));
      String word = args[0];
      String[] lemmas = dict.getLemmas(word,"NN");
      for (int li=0,ln=lemmas.length;li<ln;li++) {
        for (int si=0,sn=dict.getNumSenses(lemmas[li],"NN");si<sn;si++) {
          System.out.println(lemmas[li]+" ("+si+")\t"+java.util.Arrays.asList(dict.getParentSenseKeys(lemmas[li],"NN",si)));
        }
      }
    }
  }
}
