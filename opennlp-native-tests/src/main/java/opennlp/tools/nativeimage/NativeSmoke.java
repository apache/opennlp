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

package opennlp.tools.nativeimage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.stopword.StopwordLists;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;
import opennlp.tools.util.Version;
import opennlp.tools.util.jvm.NativeImage;
import opennlp.tools.util.model.ModelLoader;

/**
 * Smoke test for a GraalVM native image of OpenNLP.
 * <p>
 * Loads a sentence, tokenizer, POS and name finder model from the directory
 * given as the only argument, runs the four components over a fixed text and
 * checks the results. Exits with status 1 and a {@code FAIL} line on the first
 * wrong result, so that a CI job fails when a code path stops working in the image.
 * <p>
 * Expected files in the model directory:
 * <ul>
 *   <li>{@code opennlp-en-ud-ewt-sentence-1.3-2.5.4.bin}</li>
 *   <li>{@code opennlp-en-ud-ewt-tokens-1.3-2.5.4.bin}</li>
 *   <li>{@code opennlp-en-ud-ewt-pos-1.3-2.5.4.bin}</li>
 *   <li>{@code en-ner-person.bin}</li>
 * </ul>
 */
public final class NativeSmoke {

  static final String SENTENCE_MODEL = "opennlp-en-ud-ewt-sentence-1.3-2.5.4.bin";
  static final String TOKENIZER_MODEL = "opennlp-en-ud-ewt-tokens-1.3-2.5.4.bin";
  static final String POS_MODEL = "opennlp-en-ud-ewt-pos-1.3-2.5.4.bin";
  static final String NER_MODEL = "en-ner-person.bin";

  static final String TEXT = "Pierre Vinken, 61 years old, will join the board as a nonexecutive director. "
      + "Mr. Vinken is chairman of Elsevier N.V., the Dutch publishing group.";

  private NativeSmoke() {
  }

  public static void main(String[] args) {
    if (args.length != 1) {
      System.err.println("usage: " + NativeSmoke.class.getSimpleName() + " <model directory>");
      System.exit(2);
    }
    final Path dir = Path.of(args[0]);
    final List<String> failures = new ArrayList<>();
    try {
      run(dir, failures);
    } catch (Exception e) {
      failures.add("exception: " + e);
      e.printStackTrace(System.err);
    }
    if (failures.isEmpty()) {
      System.out.println("OK: OpenNLP " + Version.currentVersion() + " smoke test passed"
          + (NativeImage.inImageRuntime() ? " in a native image" : " on the JVM"));
    } else {
      for (String failure : failures) {
        System.out.println("FAIL: " + failure);
      }
      System.exit(1);
    }
  }

  private static void run(Path dir, List<String> failures) throws IOException {
    final SentenceModel sentenceModel = load(dir, SENTENCE_MODEL, SentenceModel.class);
    final TokenizerModel tokenizerModel = load(dir, TOKENIZER_MODEL, TokenizerModel.class);
    final POSModel posModel = load(dir, POS_MODEL, POSModel.class);
    final TokenNameFinderModel nerModel = load(dir, NER_MODEL, TokenNameFinderModel.class);

    final String[] sentences = new SentenceDetectorME(sentenceModel).sentDetect(TEXT);
    System.out.println("sentences: " + Arrays.toString(sentences));
    check(failures, sentences.length == 2, "expected 2 sentences, got " + sentences.length);
    check(failures, sentences.length > 0 && sentences[0].startsWith("Pierre Vinken"),
        "first sentence should start with the name");

    final String[] tokens = new TokenizerME(tokenizerModel).tokenize(sentences[0]);
    System.out.println("tokens: " + Arrays.toString(tokens));
    check(failures, tokens.length == 16, "expected 16 tokens, got " + tokens.length);
    check(failures, Arrays.asList(tokens).contains("61"), "tokens should contain 61");
    check(failures, Arrays.asList(tokens).contains(","), "the comma should be its own token");

    final String[] tags = new POSTaggerME(posModel).tag(tokens);
    System.out.println("tags: " + Arrays.toString(tags));
    check(failures, tags.length == tokens.length, "one tag per token");
    check(failures, tags.length > 1 && "PROPN".equals(tags[1]), "Vinken should be tagged PROPN");
    check(failures, Arrays.asList(tags).contains("NUM"), "61 should be tagged NUM");

    final NameFinderME nameFinder = new NameFinderME(nerModel);
    final Span[] names = nameFinder.find(tokens);
    nameFinder.clearAdaptiveData();
    final List<String> found = Arrays.asList(Span.spansToStrings(names, tokens));
    System.out.println("names: " + found);
    check(failures, found.contains("Pierre Vinken"), "the person name should be found");

    // resources read by a name computed at run time need reachability metadata
    check(failures, StopwordLists.forLanguage("en").isStopword("the"),
        "bundled English stopword list should be readable");
    check(failures, StopwordLists.forLanguage("de").isStopword("und"),
        "bundled German stopword list should be readable");
  }

  private static <T extends opennlp.tools.util.model.BaseModel> T load(Path dir, String name, Class<T> type)
      throws IOException {
    final Path file = dir.resolve(name);
    if (!Files.isRegularFile(file)) {
      throw new IOException("model file not found: " + file.toAbsolutePath());
    }
    try (InputStream in = Files.newInputStream(file)) {
      // the registry route: the model class is resolved by name, without reflection
      return ModelLoader.forType(type).load(in);
    }
  }

  private static void check(List<String> failures, boolean condition, String message) {
    if (!condition) {
      failures.add(message);
    }
  }
}
