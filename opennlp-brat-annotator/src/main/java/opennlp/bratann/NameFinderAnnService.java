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

package opennlp.bratann;

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

import javax.ws.rs.core.UriBuilder;

import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.sentdetect.NewlineSentenceDetector;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.tokenize.WhitespaceTokenizer;

public class NameFinderAnnService {

  public static SentenceDetector sentenceDetector = new NewlineSentenceDetector();
  public static Tokenizer tokenizer = WhitespaceTokenizer.INSTANCE;
  public static TokenNameFinder nameFinders[];

  public static void main(String[] args) throws Exception {

    if (args.length == 0) {
      System.out.println("Usage:");
      System.out.println("[NameFinderAnnService -serverPort port] [-tokenizerModel file] "
          + "[-ruleBasedTokenizer whitespace|simple] "
          + "[-sentenceDetectorModel file] namefinderFile|nameFinderURI");
      return;
    }

    List<String> argList = Arrays.asList(args);

    int serverPort = 8080;
    int serverPortIndex = argList.indexOf("-serverPort") + 1;

    if (serverPortIndex > 0 && serverPortIndex < args.length) {
      serverPort = Integer.parseInt(args[serverPortIndex]);
    }

    int sentenceModelIndex = argList.indexOf("-sentenceDetectorModel") + 1;
    if (sentenceModelIndex > 0 && sentenceModelIndex < args.length) {
      sentenceDetector = new SentenceDetectorME(
          new SentenceModel(new File(args[sentenceModelIndex])));
    }

    int ruleBasedTokenizerIndex = argList.indexOf("-ruleBasedTokenizer") + 1;

    if (ruleBasedTokenizerIndex > 0 && ruleBasedTokenizerIndex < args.length) {
      if ("whitespace".equals(args[ruleBasedTokenizerIndex])) {
        tokenizer = WhitespaceTokenizer.INSTANCE;
      } else if ("simple".equals(args[ruleBasedTokenizerIndex])) {
        tokenizer = SimpleTokenizer.INSTANCE;
      } else {
        System.out.println("unkown tokenizer: " + args[ruleBasedTokenizerIndex]);
        return;
      }
    }

    int tokenizerModelIndex = argList.indexOf("-tokenizerModel") + 1;
    if (tokenizerModelIndex > 0 && tokenizerModelIndex < args.length) {
      tokenizer = new TokenizerME(
          new TokenizerModel(new File(args[tokenizerModelIndex])));
    }

    nameFinders = new TokenNameFinder[] {new NameFinderME(
        new TokenNameFinderModel(new File(args[args.length - 1])))};

    URI baseUri = UriBuilder.fromUri("http://localhost/").port(serverPort).build();
    ResourceConfig config = new ResourceConfig(NameFinderResource.class);
    GrizzlyHttpServerFactory.createHttpServer(baseUri, config);
  }
}
