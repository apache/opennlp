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

package opennlp.tools.eval;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import opennlp.tools.chunker.Chunker;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.cmdline.parser.ParserTool;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.Parser;
import opennlp.tools.parser.ParserFactory;
import opennlp.tools.parser.ParserModel;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSSample;
import opennlp.tools.postag.POSTagger;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.MarkableFileInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;

/**
 * This tests ensures that the existing SourceForge models perform
 * like they are expected to.
 * <p>
 * To run this tests external the leipzig sentences files is needed:
 * leipzig/eng_news_2010_300K-sentences.txt, this file can be
 * obtained from the leipzig corpus project. <br>
 * <p>
 * And all the SourceForge models:<br>
 * - models-sf/en-sent.bin<br>
 * - models-sf/en-token.bin<br>
 * - models-sf/en-ner-date.bin<br>
 * - models-sf/en-ner-location.binn<br>
 * - models-sf/en-ner-money.bin<br>
 * - models-sf/en-ner-organization.bin<br>
 * - models-sf/en-ner-percentage.bi<br>
 * - models-sf/en-ner-person.bin<br>
 * - models-sf/en-ner-time.bin<br>
 * - models-sf/en-chunker.bin<br>
 * - models-sf/en-pos-maxent.bin<br>
 * - models-sf/en-pos-perceptron.bin<br>
 * - models-sf/en-parser-chunking.bin.bin<br>
 */
public class SourceForgeModelEval extends AbstractEvalTest {

  private static class LeipzigTestSample {
    private final List<String> text;

    private LeipzigTestSample(String[] text) {
      Objects.requireNonNull(text, "text must not be null");
      this.text = Collections.unmodifiableList(new ArrayList<>(Arrays.asList(text)));
    }

    public String[] getText() {
      return text.toArray(new String[text.size()]);
    }

    @Override
    public String toString() {

      StringBuilder sampleString = new StringBuilder("eng");

      sampleString.append('\t');

      for (String s : text) {
        sampleString.append(s).append(' ');
      }

      if (sampleString.length() > 0) {
        // remove last space
        sampleString.setLength(sampleString.length() - 1);
      }

      return sampleString.toString();
    }
  }

  private static class LeipzigTestSampleStream extends FilterObjectStream<String, LeipzigTestSample> {

    private final int sentencePerDocument;
    private final Tokenizer tokenizer;

    private LeipzigTestSampleStream(int sentencePerDocument, Tokenizer tokenizer, InputStreamFactory in)
            throws IOException {
      super(new PlainTextByLineStream(in, StandardCharsets.UTF_8));
      this.sentencePerDocument = sentencePerDocument;
      this.tokenizer = tokenizer;
    }

    @Override
    public LeipzigTestSample read() throws IOException {
      int count = 0;
      List<String> tokensList = new ArrayList<>();

      String line;
      while (count < sentencePerDocument && (line = samples.read()) != null) {

        String[] tokens = tokenizer.tokenize(line);

        if (tokens.length == 0) {
          throw new IOException("Empty lines are not allowed!");
        }

        // Always skip first token, that is the sentence number!
        tokensList.addAll(Arrays.asList(tokens).subList(1, tokens.length));

        count++;
      }

      if (tokensList.size() > 0) {
        return new LeipzigTestSample(tokensList.toArray(new String[tokensList.size()]));
      }

      return null;
    }
  }

  @BeforeClass
  public static void verifyTrainingData() throws Exception {
    verifyTrainingData(new LeipzigTestSampleStream(25, SimpleTokenizer.INSTANCE,
            new MarkableFileInputStreamFactory(new File(getOpennlpDataDir(),
                    "leipzig/eng_news_2010_300K-sentences.txt"))),
        new BigInteger("172812413483919324675263268750583851712"));
  }

  @Test
  public void evalSentenceModel() throws Exception {

    SentenceModel model = new SentenceModel(
            new File(getOpennlpDataDir(), "models-sf/en-sent.bin"));

    MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);

    SentenceDetector sentenceDetector = new SentenceDetectorME(model);

    StringBuilder text = new StringBuilder();

    try (ObjectStream<LeipzigTestSample> lineBatches = new LeipzigTestSampleStream(25,
            SimpleTokenizer.INSTANCE,
            new MarkableFileInputStreamFactory(new File(getOpennlpDataDir(),
                    "leipzig/eng_news_2010_300K-sentences.txt")))) {

      LeipzigTestSample lineBatch;
      while ((lineBatch = lineBatches.read()) != null) {
        text.append(String.join(" ", lineBatch.getText())).append(" ");
      }
    }

    String[] sentences = sentenceDetector.sentDetect(text.toString());

    for (String sentence : sentences) {
      digest.update(sentence.getBytes(StandardCharsets.UTF_8));
    }

    Assert.assertEquals(new BigInteger("228544068397077998410949364710969159291"),
            new BigInteger(1, digest.digest()));
  }

  @Test
  public void evalTokenModel() throws Exception {

    // the input stream is currently tokenized, we should detokenize it again,
    //    (or extend to pass in tokenizer, then whitespace tokenizer can be passed)
    // and then tokenize it here

    TokenizerModel model = new TokenizerModel(
            new File(getOpennlpDataDir(), "models-sf/en-token.bin"));

    MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);

    Tokenizer tokenizer = new TokenizerME(model);

    try (ObjectStream<LeipzigTestSample> lines = new LeipzigTestSampleStream(1,
            WhitespaceTokenizer.INSTANCE,
            new MarkableFileInputStreamFactory(new File(getOpennlpDataDir(),
                    "leipzig/eng_news_2010_300K-sentences.txt")))) {

      LeipzigTestSample line;
      while ((line = lines.read()) != null) {
        String[] tokens = tokenizer.tokenize(String.join(" ", line.getText()));
        for (String token : tokens) {
          digest.update(token.getBytes(StandardCharsets.UTF_8));
        }
      }
    }

    Assert.assertEquals(new BigInteger("180602607571756839321060482558626151930"),
            new BigInteger(1, digest.digest()));
  }

  private ObjectStream<LeipzigTestSample> createLineWiseStream() throws IOException {
    return new LeipzigTestSampleStream(1,
        SimpleTokenizer.INSTANCE,
        new MarkableFileInputStreamFactory(new File(getOpennlpDataDir(),
            "leipzig/eng_news_2010_300K-sentences.txt")));
  }


  private void evalNameFinder(TokenNameFinderModel model, BigInteger expectedHash)
      throws Exception {

    MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);

    TokenNameFinder nameFinder = new NameFinderME(model);

    try (ObjectStream<LeipzigTestSample> lines = createLineWiseStream()) {

      LeipzigTestSample line;
      while ((line = lines.read()) != null) {
        Span[] names = nameFinder.find(line.getText());
        for (Span name : names) {
          digest.update((name.getType() + name.getStart()
              + name.getEnd()).getBytes(StandardCharsets.UTF_8));
        }
      }
    }

    Assert.assertEquals(expectedHash, new BigInteger(1, digest.digest()));
  }

  @Test
  public void evalNerDateModel() throws Exception {
    TokenNameFinderModel personModel = new TokenNameFinderModel(
        new File(getOpennlpDataDir(), "models-sf/en-ner-date.bin"));

    evalNameFinder(personModel, new BigInteger("116570003910213570906062355532299200317"));
  }

  @Test
  public void evalNerLocationModel() throws Exception {
    TokenNameFinderModel personModel = new TokenNameFinderModel(
        new File(getOpennlpDataDir(), "models-sf/en-ner-location.bin"));

    evalNameFinder(personModel, new BigInteger("44810593886021404716125849669208680993"));
  }

  @Test
  public void evalNerMoneyModel() throws Exception {
    TokenNameFinderModel personModel = new TokenNameFinderModel(
        new File(getOpennlpDataDir(), "models-sf/en-ner-money.bin"));

    evalNameFinder(personModel, new BigInteger("65248897509365807977219790824670047287"));
  }

  @Test
  public void evalNerOrganizationModel() throws Exception {
    TokenNameFinderModel personModel = new TokenNameFinderModel(
        new File(getOpennlpDataDir(), "models-sf/en-ner-organization.bin"));

    evalNameFinder(personModel, new BigInteger("50454559690338630659278005157657197233"));
  }

  @Test
  public void evalNerPercentageModel() throws Exception {
    TokenNameFinderModel personModel = new TokenNameFinderModel(
        new File(getOpennlpDataDir(), "models-sf/en-ner-percentage.bin"));

    evalNameFinder(personModel, new BigInteger("320996882594215344113023719117249515343"));
  }

  @Test
  public void evalNerPersonModel() throws Exception {
    TokenNameFinderModel personModel = new TokenNameFinderModel(
        new File(getOpennlpDataDir(), "models-sf/en-ner-person.bin"));

    evalNameFinder(personModel, new BigInteger("143619582249937129618340838626447763744"));
  }

  @Test
  public void evalNerTimeModel() throws Exception {
    TokenNameFinderModel personModel = new TokenNameFinderModel(
        new File(getOpennlpDataDir(), "models-sf/en-ner-time.bin"));

    evalNameFinder(personModel, new BigInteger("282941772380683328816791801782579055940"));
  }

  @Test
  public void evalChunkerModel() throws Exception {

    MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);

    POSTagger tagger = new POSTaggerME(new POSModel(
        new File(getOpennlpDataDir(), "models-sf/en-pos-perceptron.bin")));

    Chunker chunker = new ChunkerME(new ChunkerModel(
        new File(getOpennlpDataDir(), "models-sf/en-chunker.bin")));

    try (ObjectStream<LeipzigTestSample> lines = createLineWiseStream()) {

      LeipzigTestSample line;
      while ((line = lines.read()) != null) {
        POSSample sentence = new POSSample(line.getText(), tagger.tag(line.getText()));

        String[] chunks = chunker.chunk(sentence.getSentence(), sentence.getTags());
        for (String chunk : chunks) {
          digest.update(chunk.getBytes(StandardCharsets.UTF_8));
        }
      }
    }

    Assert.assertEquals(new BigInteger("226003515785585284478071030961407561943"),
        new BigInteger(1, digest.digest()));
  }

  private void evalPosModel(POSModel model, BigInteger expectedHash) throws Exception {

    // break the input stream into sentences
    // The input stream is tokenized and can be processed here directly

    MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);

    POSTagger tagger = new POSTaggerME(model);

    try (ObjectStream<LeipzigTestSample> lines = createLineWiseStream()) {

      LeipzigTestSample line;
      while ((line = lines.read()) != null) {
        String[] tags = tagger.tag(line.getText());
        for (String tag : tags) {
          digest.update(tag.getBytes(StandardCharsets.UTF_8));
        }
      }
    }

    Assert.assertEquals(expectedHash, new BigInteger(1, digest.digest()));
  }

  @Test
  public void evalMaxentModel() throws Exception {
    POSModel maxentModel = new POSModel(
        new File(getOpennlpDataDir(), "models-sf/en-pos-maxent.bin"));

    evalPosModel(maxentModel, new BigInteger("231995214522232523777090597594904492687"));
  }

  @Test
  public void evalPerceptronModel() throws Exception {
    POSModel perceptronModel = new POSModel(
        new File(getOpennlpDataDir(), "models-sf/en-pos-perceptron.bin"));

    evalPosModel(perceptronModel, new BigInteger("209440430718727101220960491543652921728"));
  }

  @Test
  public void evalParserModel() throws Exception {

    ParserModel model = new ParserModel(
        new File(getOpennlpDataDir(), "models-sf/en-parser-chunking.bin"));

    MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);

    Parser parser = ParserFactory.create(model);

    try (ObjectStream<LeipzigTestSample> lines = createLineWiseStream()) {

      LeipzigTestSample line;
      while ((line = lines.read()) != null) {
        Parse[] parse = ParserTool.parseLine(String.join(" ", line.getText()), parser, 1);
        if (parse.length > 0) {
          StringBuffer sb = new StringBuffer();
          parse[0].show(sb);
          digest.update(sb.toString().getBytes(StandardCharsets.UTF_8));
        } else {
          digest.update("empty".getBytes(StandardCharsets.UTF_8));
        }
      }
    }

    Assert.assertEquals(new BigInteger("68039262350771988792233880373220954061"),
        new BigInteger(1, digest.digest()));
  }
}
