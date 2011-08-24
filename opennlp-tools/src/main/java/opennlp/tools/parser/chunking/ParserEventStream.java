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

package opennlp.tools.parser.chunking;

import java.io.FileInputStream;
import java.util.List;

import opennlp.model.Event;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.parser.AbstractBottomUpParser;
import opennlp.tools.parser.AbstractParserEventStream;
import opennlp.tools.parser.HeadRules;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.ParseSampleStream;
import opennlp.tools.parser.ParserEventTypeEnum;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

/**
 * Wrapper class for one of four parser event streams.  The particular event stream is specified
 * at construction.
 */
public class ParserEventStream extends AbstractParserEventStream {

  protected BuildContextGenerator bcg;
  protected CheckContextGenerator kcg;

  /**
   * Create an event stream based on the specified data stream of the specified type using the specified head rules.
   * @param d A 1-parse-per-line Penn Treebank Style parse.
   * @param rules The head rules.
   * @param etype The type of events desired (tag, chunk, build, or check).
   * @param dict A tri-gram dictionary to reduce feature generation.
   */
  public ParserEventStream(ObjectStream<Parse> d, HeadRules rules, ParserEventTypeEnum etype, Dictionary dict) {
    super(d,rules,etype,dict);
  }

  @Override
  protected void init() {
    if (etype == ParserEventTypeEnum.BUILD) {
      this.bcg = new BuildContextGenerator(dict);
    }
    else if (etype == ParserEventTypeEnum.CHECK) {
      this.kcg = new CheckContextGenerator();
    }
  }



  public ParserEventStream(ObjectStream<Parse> d, HeadRules rules, ParserEventTypeEnum etype) {
    this (d,rules,etype,null);
  }

  /**
   * Returns true if the specified child is the first child of the specified parent.
   * @param child The child parse.
   * @param parent The parent parse.
   * @return true if the specified child is the first child of the specified parent; false otherwise.
   */
  protected boolean firstChild(Parse child, Parse parent) {
    return AbstractBottomUpParser.collapsePunctuation(parent.getChildren(), punctSet)[0] == child;
  }

  public static  Parse[] reduceChunks(Parse[] chunks, int ci, Parse parent) {
    String type = parent.getType();
    //  perform reduce
    int reduceStart = ci;
    int reduceEnd = ci;
    while (reduceStart >=0 && chunks[reduceStart].getParent() == parent) {
      reduceStart--;
    }
    reduceStart++;
    Parse[] reducedChunks;
    if (!type.equals(AbstractBottomUpParser.TOP_NODE)) {
      reducedChunks = new Parse[chunks.length-(reduceEnd-reduceStart+1)+1]; //total - num_removed + 1 (for new node)
      //insert nodes before reduction
      for (int ri=0,rn=reduceStart;ri<rn;ri++) {
        reducedChunks[ri]=chunks[ri];
      }
      //insert reduced node
      reducedChunks[reduceStart]=parent;
      //propagate punctuation sets
      parent.setPrevPunctuation(chunks[reduceStart].getPreviousPunctuationSet());
      parent.setNextPunctuation(chunks[reduceEnd].getNextPunctuationSet());
      //insert nodes after reduction
      int ri=reduceStart+1;
      for (int rci=reduceEnd+1;rci<chunks.length;rci++) {
        reducedChunks[ri]=chunks[rci];
        ri++;
      }
      ci=reduceStart-1; //ci will be incremented at end of loop
    }
    else {
      reducedChunks = new Parse[0];
    }
    return reducedChunks;
  }

  /**
   * Adds events for parsing (post tagging and chunking to the specified list of events for the specified parse chunks.
   * @param parseEvents The events for the specified chunks.
   * @param chunks The incomplete parses to be parsed.
   */
  @Override
  protected void addParseEvents(List<Event> parseEvents, Parse[] chunks) {
    int ci = 0;
    while (ci < chunks.length) {
      //System.err.println("parserEventStream.addParseEvents: chunks="+Arrays.asList(chunks));
      Parse c = chunks[ci];
      Parse parent = c.getParent();
      if (parent != null) {
        String type = parent.getType();
        String outcome;
        if (firstChild(c, parent)) {
          outcome = AbstractBottomUpParser.START + type;
        }
        else {
          outcome = AbstractBottomUpParser.CONT + type;
        }
        //System.err.println("parserEventStream.addParseEvents: chunks["+ci+"]="+c+" label="+outcome+" bcg="+bcg);
        c.setLabel(outcome);
        if (etype == ParserEventTypeEnum.BUILD) {
          parseEvents.add(new Event(outcome, bcg.getContext(chunks, ci)));
        }
        int start = ci - 1;
        while (start >= 0 && chunks[start].getParent() == parent) {
          start--;
        }
        if (lastChild(c, parent)) {
          if (etype == ParserEventTypeEnum.CHECK) {
            parseEvents.add(new Event(Parser.COMPLETE, kcg.getContext( chunks, type, start + 1, ci)));
          }
          //perform reduce
          int reduceStart = ci;
          while (reduceStart >=0 && chunks[reduceStart].getParent() == parent) {
            reduceStart--;
          }
          reduceStart++;
          chunks = reduceChunks(chunks,ci,parent);
          ci=reduceStart-1; //ci will be incremented at end of loop
        }
        else {
          if (etype == ParserEventTypeEnum.CHECK) {
            parseEvents.add(new Event(Parser.INCOMPLETE, kcg.getContext(chunks, type, start + 1, ci)));
          }
        }
      }
      ci++;
    }
  }

  public static void main(String[] args) throws java.io.IOException, InvalidFormatException {
    if (args.length == 0) {
      System.err.println("Usage ParserEventStream -[tag|chunk|build|check|fun] head_rules [dictionary] < parses");
      System.exit(1);
    }
    ParserEventTypeEnum etype = null;
    boolean fun = false;
    int ai = 0;
    while (ai < args.length && args[ai].startsWith("-")) {
      if (args[ai].equals("-build")) {
        etype = ParserEventTypeEnum.BUILD;
      }
      else if (args[ai].equals("-check")) {
        etype = ParserEventTypeEnum.CHECK;
      }
      else if (args[ai].equals("-chunk")) {
        etype = ParserEventTypeEnum.CHUNK;
      }
      else if (args[ai].equals("-tag")) {
        etype = ParserEventTypeEnum.TAG;
      }
      else if (args[ai].equals("-fun")) {
        fun = true;
      }
      else {
        System.err.println("Invalid option " + args[ai]);
        System.exit(1);
      }
      ai++;
    }
    HeadRules rules = new opennlp.tools.parser.lang.en.HeadRules(args[ai++]);
    Dictionary dict = null;
    if (ai < args.length) {
      dict = new Dictionary(new FileInputStream(args[ai++]),true);
    }
    if (fun) {
      Parse.useFunctionTags(true);
    }
    opennlp.model.EventStream es = new ParserEventStream(new ParseSampleStream(new PlainTextByLineStream(new java.io.InputStreamReader(System.in))), rules, etype, dict);
    while (es.hasNext()) {
      System.out.println(es.next());
    }
  }
}

