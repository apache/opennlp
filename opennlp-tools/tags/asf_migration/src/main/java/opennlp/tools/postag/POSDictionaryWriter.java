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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import opennlp.tools.util.CountedSet;

/**
 * Class for writing a pos-tag-dictionary to a file.
 */
@Deprecated
public class POSDictionaryWriter {

  private Writer dictFile;
  private Map<String, Set<String>> dictionary;
  private CountedSet<String> wordCounts;
  private String newline = System.getProperty("line.separator");

  public POSDictionaryWriter(String file, String encoding) throws IOException {
    if (encoding != null) {
      dictFile = new OutputStreamWriter(new FileOutputStream(file),encoding);
    }
    else {
      dictFile = new FileWriter(file);
    }
    dictionary = new HashMap<String, Set<String>>();
    wordCounts = new CountedSet<String>();
  }

  public POSDictionaryWriter(String file) throws IOException {
    this(file,null);
  }

  public void addEntry(String word, String tag) {
    Set<String> tags = dictionary.get(word);
    if (tags == null) {
      tags = new HashSet<String>();
      dictionary.put(word,tags);
    }
    tags.add(tag);
    wordCounts.add(word);
  }

  public void write() throws IOException {
    write(5);
  }

  public void write(int cutoff) throws IOException {
    for (Iterator<String> wi = wordCounts.iterator(); wi.hasNext();) {
      String word = wi.next();
      if (wordCounts.getCount(word) >= cutoff) {
        dictFile.write(word);
        Set<String> tags = dictionary.get(word);
        for (Iterator<String> ti=tags.iterator();ti.hasNext();) {
          dictFile.write(" ");
          dictFile.write(ti.next());
        }
        dictFile.write(newline);
      }
    }
    dictFile.close();
  }

  private static void usage() {
    System.err.println("Usage: POSDictionaryWriter [-encoding encoding] dictionary tag_files");
    System.exit(1);
  }

  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      usage();
    }
    int ai=0;
    String encoding = null;
    if (args[ai].startsWith("-encoding")) {
      if (ai+1 >= args.length) {
        usage();
      }
      else {
        encoding = args[ai+1];
        ai+=2;
      }
    }
    String dictionaryFile = args[ai++];
    POSDictionaryWriter dict = new POSDictionaryWriter(dictionaryFile,encoding);
    for (int fi=ai;fi<args.length;fi++) {
      BufferedReader in;
      if (encoding == null) {
        in = new BufferedReader(new FileReader(args[fi]));
      }
      else {
        in = new BufferedReader(new InputStreamReader(new FileInputStream(args[fi]),encoding));
      }
      for (String line=in.readLine();line != null; line = in.readLine()) {
        if (!line.equals("")) {
          String[] parts = line.split("\\s+");
          for (int pi=0;pi<parts.length;pi++) {
            int index = parts[pi].lastIndexOf('_');
            String word = parts[pi].substring(0,index);
            String tag = parts[pi].substring(index+1);
            dict.addEntry(word,tag);
          }
        }
      }
    }
    dict.write();
  }
}
