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

package opennlp.tools.tokenize.lang.en;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.util.Span;

/**
 * Class which produces an Iterator<TokenSample> from a file of space delimited token.
 * This class uses a number of English-specific hueristics to un-separate tokens which
 * are typically found together in text.
 */
public class TokenSampleStream implements Iterator<TokenSample> {

  private BufferedReader in;
  private String line;
  private Pattern alphaNumeric = Pattern.compile("[A-Za-z0-9]");
  private boolean evenq = true;

  public TokenSampleStream(InputStream is) throws IOException {
    this.in = new BufferedReader(new InputStreamReader(is));
    line = in.readLine();
  }

  public boolean hasNext() {
    return line != null;
  }

  public TokenSample next() {
    String[] tokens = line.split("\\s+");
    if (tokens.length == 0) {
      evenq =true;
    }
    StringBuffer sb = new StringBuffer(line.length());
    List<Span> spans = new ArrayList<Span>();
    int length = 0;
    for (int ti=0;ti<tokens.length;ti++) {
      String token = tokens[ti];
      String lastToken = ti -1 >= 0 ? tokens[ti-1] : "";
      if (token.equals("-LRB-")) {
        token = "(";
      }
      else if (token.equals("-LCB-")) {
        token = "{";
      }
      else if (token.equals("-RRB-")) {
        token = ")";
      }
      else if (token.equals("-RCB-")) {
        token = "}";
      }
      if (sb.length() == 0) {

      }
      else if (!alphaNumeric.matcher(token).find() || token.startsWith("'") || token.equalsIgnoreCase("n't")) {
        if ((token.equals("``") || token.equals("--") || token.equals("$") ||
            token.equals("(")  || token.equals("&")  || token.equals("#") ||
            (token.equals("\"") && (evenq && ti != tokens.length-1)))
            && (!lastToken.equals("(") || !lastToken.equals("{"))) {
          //System.out.print(" "+token);
          length++;
        }
        else {
          //System.out.print(token);
        }
      }
      else {
        if (lastToken.equals("``") || (lastToken.equals("\"") && !evenq) || lastToken.equals("(") || lastToken.equals("{")
            || lastToken.equals("$") || lastToken.equals("#")) {
          //System.out.print(token);
        }
        else {
          //System.out.print(" "+token);
          length++;
        }
      }
      if (token.equals("\"")) {
        if (ti == tokens.length -1) {
          evenq=true;
        }
        else {
          evenq = !evenq;
        }
      }
      if (sb.length() < length) {
        sb.append(" ");
      }
      sb.append(token);
      spans.add(new Span(length,length+token.length()));
      length+=token.length();
    }
    //System.out.println();
    try {
      line = in.readLine();
    } catch (IOException e) {
      e.printStackTrace();
      line = null;
    }
    return new TokenSample(sb.toString(),spans.toArray(new Span[spans.size()]));
  }


  public void remove() {
    throw new UnsupportedOperationException();
  }

  private static void usage() {
    System.err.println("TokenSampleStream [-spans] < in");
    System.err.println("Where in is a space delimited list of tokens.");
  }

  public static void main(String[] args) throws IOException {
    boolean showSpans = false;
    int ai=0;
    while (ai < args.length) {
      if (args[ai].equals("-spans")) {
        showSpans = true;
      }
      else {
        System.err.println("Unknown option "+args[ai]);
        usage();
      }
      ai++;
    }
    TokenSampleStream tss = new TokenSampleStream(System.in);
    while(tss.hasNext()) {
      TokenSample ts = tss.next();
      String text = ts.getText();
      System.out.println(text);
      Span[] tokenSpans = ts.getTokenSpans();
      int ti=0;
      if (showSpans) {
        for (int i=0;i<text.length();i++) {
          if (ti-1 >= 0 && i==tokenSpans[ti-1].getEnd()-1) {
            System.out.print("]");
          }
          else if (i==tokenSpans[ti].getStart()) {
            ti++;
            if (ti-1 >= 0 && i==tokenSpans[ti-1].getEnd()-1) {
              System.out.print("|");
            }
            else {
              System.out.print("[");
            }
          }
          else {
            System.out.print("-");
          }
        }
        System.out.println();
      }
    }
  }
}
