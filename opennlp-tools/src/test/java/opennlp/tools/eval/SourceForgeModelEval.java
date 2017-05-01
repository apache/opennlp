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
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import opennlp.tools.chunker.Chunker;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.cmdline.parser.ParserTool;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.formats.LeipzigDoccatSampleStream;
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
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.tokenize.WhitespaceTokenizer;
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
public class SourceForgeModelEval {

  private static MessageDigest createDigest() {
    try {
      return MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  @BeforeClass
  public static void ensureTestDataIsCorrect() throws IOException {
    MessageDigest digest = createDigest();

    try (ObjectStream<String> lines = new PlainTextByLineStream(
        new MarkableFileInputStreamFactory(new File(EvalUtil.getOpennlpDataDir(),
            "leipzig/eng_news_2010_300K-sentences.txt")), Charset.forName("UTF-8"))) {

      String line;
      while ((line = lines.read()) != null) {
        digest.update(line.getBytes("UTF-8"));
      }

      Assert.assertEquals(new BigInteger("248567841356936801447294643695012852392"),
          new BigInteger(1, digest.digest()));
    }
  }

  @Test
  public void evalSentenceModel() throws IOException {

    SentenceModel model = new SentenceModel(
        new File(EvalUtil.getOpennlpDataDir(), "models-sf/en-sent.bin"));

    MessageDigest digest = createDigest();

    SentenceDetector sentenceDetector = new SentenceDetectorME(model);

    StringBuilder text = new StringBuilder();

    try (ObjectStream<DocumentSample> lineBatches = new LeipzigDoccatSampleStream("en", 25,
        new MarkableFileInputStreamFactory(new File(EvalUtil.getOpennlpDataDir(),
            "leipzig/eng_news_2010_300K-sentences.txt")))) {

      DocumentSample lineBatch;
      while ((lineBatch = lineBatches.read()) != null) {
        text.append(String.join(" ", lineBatch.getText())).append(" ");
      }
    }

    String[] sentences = sentenceDetector.sentDetect(text.toString());

    for (String sentence : sentences) {
      digest.update(sentence.getBytes("UTF-8"));
    }

    Assert.assertEquals(new BigInteger("228544068397077998410949364710969159291"),
        new BigInteger(1, digest.digest()));
  }

  @Test
  public void evalTokenModel() throws IOException {

    // the input stream is currently tokenized, we should detokenize it again,
    //    (or extend to pass in tokenizer, then whitespace tokenizer can be passed)
    // and then tokenize it here

    TokenizerModel model = new TokenizerModel(
        new File(EvalUtil.getOpennlpDataDir(), "models-sf/en-token.bin"));

    MessageDigest digest = createDigest();

    Tokenizer tokenizer = new TokenizerME(model);

    try (ObjectStream<DocumentSample> lines = new LeipzigDoccatSampleStream("en", 1,
        WhitespaceTokenizer.INSTANCE,
        new MarkableFileInputStreamFactory(new File(EvalUtil.getOpennlpDataDir(),
            "leipzig/eng_news_2010_300K-sentences.txt")))) {

      DocumentSample line;
      while ((line = lines.read()) != null) {
        String[] tokens = tokenizer.tokenize(String.join(" ", line.getText()));
        for (String token : tokens) {
          digest.update(token.getBytes("UTF-8"));
        }
      }
    }

    Assert.assertEquals(new BigInteger("180602607571756839321060482558626151930"),
        new BigInteger(1, digest.digest()));
  }

  private ObjectStream<DocumentSample> createLineWiseStream() throws IOException {
    return new LeipzigDoccatSampleStream("en", 1,
        new MarkableFileInputStreamFactory(new File(EvalUtil.getOpennlpDataDir(),
            "leipzig/eng_news_2010_300K-sentences.txt")));
  }


  private void evalNameFinder(TokenNameFinderModel model, BigInteger expectedHash)
      throws IOException {

    MessageDigest digest = createDigest();

    TokenNameFinder nameFinder = new NameFinderME(model);

    try (ObjectStream<DocumentSample> lines = createLineWiseStream()) {

      DocumentSample line;
      while ((line = lines.read()) != null) {
        Span[] names = nameFinder.find(line.getText());
        for (Span name : names) {
          digest.update((name.getType() + name.getStart() + name.getEnd()).getBytes("UTF-8"));
        }
      }
    }

    Assert.assertEquals(expectedHash, new BigInteger(1, digest.digest()));
  }

  @Test
  public void evalNerDateModel() throws IOException {
    TokenNameFinderModel personModel = new TokenNameFinderModel(
        new File(EvalUtil.getOpennlpDataDir(), "models-sf/en-ner-date.bin"));

    evalNameFinder(personModel, new BigInteger("116570003910213570906062355532299200317"));
  }

  @Test
  public void evalNerLocationModel() throws IOException {
    TokenNameFinderModel personModel = new TokenNameFinderModel(
        new File(EvalUtil.getOpennlpDataDir(), "models-sf/en-ner-location.bin"));

    evalNameFinder(personModel, new BigInteger("44810593886021404716125849669208680993"));
  }

  @Test
  public void evalNerMoneyModel() throws IOException {
    TokenNameFinderModel personModel = new TokenNameFinderModel(
        new File(EvalUtil.getOpennlpDataDir(), "models-sf/en-ner-money.bin"));

    evalNameFinder(personModel, new BigInteger("65248897509365807977219790824670047287"));
  }

  @Test
  public void evalNerOrganizationModel() throws IOException {
    TokenNameFinderModel personModel = new TokenNameFinderModel(
        new File(EvalUtil.getOpennlpDataDir(), "models-sf/en-ner-organization.bin"));

    evalNameFinder(personModel, new BigInteger("50454559690338630659278005157657197233"));
  }

  @Test
  public void evalNerPercentageModel() throws IOException {
    TokenNameFinderModel personModel = new TokenNameFinderModel(
        new File(EvalUtil.getOpennlpDataDir(), "models-sf/en-ner-percentage.bin"));

    evalNameFinder(personModel, new BigInteger("320996882594215344113023719117249515343"));
  }

  @Test
  public void evalNerPersonModel() throws IOException {
    TokenNameFinderModel personModel = new TokenNameFinderModel(
        new File(EvalUtil.getOpennlpDataDir(), "models-sf/en-ner-person.bin"));

    evalNameFinder(personModel, new BigInteger("143619582249937129618340838626447763744"));
  }

  @Test
  public void evalNerTimeModel() throws IOException {
    TokenNameFinderModel personModel = new TokenNameFinderModel(
        new File(EvalUtil.getOpennlpDataDir(), "models-sf/en-ner-time.bin"));

    evalNameFinder(personModel, new BigInteger("282941772380683328816791801782579055940"));
  }

  @Test
  public void evalChunkerModel() throws IOException {

    MessageDigest digest = createDigest();

    POSTagger tagger = new POSTaggerME(new POSModel(
        new File(EvalUtil.getOpennlpDataDir(), "models-sf/en-pos-perceptron.bin")));

    Chunker chunker = new ChunkerME(new ChunkerModel(
        new File(EvalUtil.getOpennlpDataDir(), "models-sf/en-chunker.bin")));

    try (ObjectStream<DocumentSample> lines = createLineWiseStream()) {

      DocumentSample line;
      while ((line = lines.read()) != null) {
        POSSample sentence = new POSSample(line.getText(), tagger.tag(line.getText()));

        String[] chunks = chunker.chunk(sentence.getSentence(), sentence.getTags());
        for (String chunk : chunks) {
          digest.update(chunk.getBytes("UTF-8"));
        }
      }
    }

    Assert.assertEquals(new BigInteger("226003515785585284478071030961407561943"),
        new BigInteger(1, digest.digest()));
  }

  private void evalPosModel(POSModel model, BigInteger expectedHash) throws IOException {

    // break the input stream into sentences
    // The input stream is tokenized and can be processed here directly

    MessageDigest digest = createDigest();

    POSTagger tagger = new POSTaggerME(model);

    try (ObjectStream<DocumentSample> lines = createLineWiseStream()) {

      DocumentSample line;
      while ((line = lines.read()) != null) {
        String[] tags = tagger.tag(line.getText());
        for (String tag : tags) {
          digest.update(tag.getBytes("UTF-8"));
        }
      }
    }

    Assert.assertEquals(expectedHash, new BigInteger(1, digest.digest()));
  }

  @Test
  public void evalMaxentModel() throws IOException {
    POSModel maxentModel = new POSModel(
        new File(EvalUtil.getOpennlpDataDir(), "models-sf/en-pos-maxent.bin"));

    evalPosModel(maxentModel, new BigInteger("231995214522232523777090597594904492687"));
  }

  @Test
  public void evalPerceptronModel() throws IOException {
    POSModel perceptronModel = new POSModel(
        new File(EvalUtil.getOpennlpDataDir(), "models-sf/en-pos-perceptron.bin"));

    evalPosModel(perceptronModel, new BigInteger("209440430718727101220960491543652921728"));
  }

  @Test
  public void evalParserModel() throws IOException {

    ParserModel model = new ParserModel(
        new File(EvalUtil.getOpennlpDataDir(), "models-sf/en-parser-chunking.bin"));

    MessageDigest digest = createDigest();

    Parser parser = ParserFactory.create(model);

    try (ObjectStream<DocumentSample> lines = createLineWiseStream()) {

      DocumentSample line;
      while ((line = lines.read()) != null) {
        Parse[] parse = ParserTool.parseLine(String.join(" ", line.getText()), parser, 1);
        if (parse.length > 0) {
          digest.update(parse[0].toString().getBytes("UTF-8"));
        } else {
          digest.update("empty".getBytes("UTF-8"));
        }
      }
    }

    Assert.assertEquals(new BigInteger("13162568910062822351942983467905626940"),
        new BigInteger(1, digest.digest()));
  }
}
