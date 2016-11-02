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

import junit.framework.Assert;
import opennlp.tools.chunker.Chunker;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.cmdline.parser.ParserTool;
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
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The tests only run if the input text files are available and those
 * are derived from the leipzig corpus.
 *
 * Next step is to replace the input texts with ones that don't have license issues.
 * Wikinews is probably a vey good source. In addition also models that
 * can be shared are required to give everyone the possibilty to run this.
 */
public class SourceForgeModelEval {

  private static MessageDigest createDigest() {
    try {
      return MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  public void evalSentenceModel() throws IOException {

    SentenceModel model = new SentenceModel(
            new File("/home/burn/opennlp-data-dir", "models-sf/en-sent.bin"));

    MessageDigest digest = createDigest();

    SentenceDetector sentenceDetector = new SentenceDetectorME(model);

    StringBuilder text = new StringBuilder();

    try (ObjectStream<String> lines = new PlainTextByLineStream(
            new MarkableFileInputStreamFactory(new File("/home/burn/opennlp-data-dir",
            "leipzig/sentences.txt")), Charset.forName("UTF-8"))) {

      String line;
      while ((line = lines.read()) != null) {
        text.append(line).append(" ");
      }
    }

    String[] sentences = sentenceDetector.sentDetect(text.toString());

    for (String sentence : sentences) {
      digest.update(sentence.getBytes("UTF-8"));
    }

    Assert.assertEquals(new BigInteger("54058993675314170033586747935067060992"),
            new BigInteger(1, digest.digest()));
  }

  @Test
  public void evalTokenModel() throws IOException {

    TokenizerModel model = new TokenizerModel(
            new File(EvalUtil.getOpennlpDataDir(), "models-sf/en-token.bin"));

    MessageDigest digest = createDigest();

    Tokenizer tokenizer = new TokenizerME(model);

    try (ObjectStream<String> lines = new PlainTextByLineStream(
            new MarkableFileInputStreamFactory(new File(EvalUtil.getOpennlpDataDir(),
            "leipzig/sentences.txt")), Charset.forName("UTF-8"))) {

      String line;
      while ((line = lines.read()) != null) {
        String[] tokens = tokenizer.tokenize(line);
        for (String token : tokens) {
          digest.update(token.getBytes("UTF-8"));
        }
      }
    }

    Assert.assertEquals(new BigInteger("309548448163611475251363008574168734058"),
            new BigInteger(1, digest.digest()));
  }

  private void evalNameFinder(TokenNameFinderModel model, BigInteger expectedHash)
      throws IOException {

    MessageDigest digest = createDigest();

    TokenNameFinder nameFinder = new NameFinderME(model);

    try (ObjectStream<String> lines = new PlainTextByLineStream(
        new MarkableFileInputStreamFactory(new File(EvalUtil.getOpennlpDataDir(), "leipzig/simpleTok.txt")),
        Charset.forName("UTF-8"))) {

      String line;
      while ((line = lines.read()) != null) {
        Span[] names = nameFinder.find(WhitespaceTokenizer.INSTANCE.tokenize(line));
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

    evalNameFinder(personModel, new BigInteger("13595680199220579055030594287753821185"));
  }

  @Test
  public void evalNerLocationModel() throws IOException {
    TokenNameFinderModel personModel = new TokenNameFinderModel(
            new File(EvalUtil.getOpennlpDataDir(), "models-sf/en-ner-location.bin"));

    evalNameFinder(personModel, new BigInteger("61423868331440897441202803979849564658"));
  }

  @Test
  public void evalNerMoneyModel() throws IOException {
    TokenNameFinderModel personModel = new TokenNameFinderModel(
            new File(EvalUtil.getOpennlpDataDir(), "models-sf/en-ner-money.bin"));

    evalNameFinder(personModel, new BigInteger("31779803056581858429003932617173745364"));
  }

  @Test
  public void evalNerOrganizationModel() throws IOException {
    TokenNameFinderModel personModel = new TokenNameFinderModel(
            new File(EvalUtil.getOpennlpDataDir(), "models-sf/en-ner-organization.bin"));

    evalNameFinder(personModel, new BigInteger("268615755804346283904103340480818555730"));
  }

  @Test
  public void evalNerPercentageModel() throws IOException {
    TokenNameFinderModel personModel = new TokenNameFinderModel(
            new File(EvalUtil.getOpennlpDataDir(), "models-sf/en-ner-percentage.bin"));

    evalNameFinder(personModel, new BigInteger("1793019183238911248412519564457497503"));
  }

  @Test
  public void evalNerPersonModel() throws IOException {
    TokenNameFinderModel personModel = new TokenNameFinderModel(
        new File(EvalUtil.getOpennlpDataDir(), "models-sf/en-ner-person.bin"));

    evalNameFinder(personModel, new BigInteger("260378080051855476096106859434660527393"));
  }

  @Test
  public void evalNerTimeModel() throws IOException {
    TokenNameFinderModel personModel = new TokenNameFinderModel(
        new File(EvalUtil.getOpennlpDataDir(), "models-sf/en-ner-time.bin"));

    evalNameFinder(personModel, new BigInteger("264798318876255738642952635833268231353"));
  }

  @Test
  public void evalChunkerModel() throws IOException {

    ChunkerModel model = new ChunkerModel(
            new File(EvalUtil.getOpennlpDataDir(), "models-sf/en-chunker.bin"));

    MessageDigest digest = createDigest();

    Chunker chunker = new ChunkerME(model);

    try (ObjectStream<String> lines = new PlainTextByLineStream(
            new MarkableFileInputStreamFactory(new File(EvalUtil.getOpennlpDataDir(), "leipzig/simpleTokPos.txt")),
            Charset.forName("UTF-8"))) {

      String line;
      while ((line = lines.read()) != null) {
        POSSample sentence = POSSample.parse(line);

        String[] chunks = chunker.chunk(sentence.getSentence(), sentence.getTags());
        for (String chunk : chunks) {
          digest.update(chunk.getBytes("UTF-8"));
        }
      }
    }

    Assert.assertEquals(new BigInteger("87766988424222321513554054789708059330"),
        new BigInteger(1, digest.digest()));
  }

  private void evalPosModel(POSModel model, BigInteger expectedHash) throws IOException {
    MessageDigest digest = createDigest();

    POSTagger tagger = new POSTaggerME(model);

    try (ObjectStream<String> lines = new PlainTextByLineStream(
            new MarkableFileInputStreamFactory(new File(EvalUtil.getOpennlpDataDir(),
            "leipzig/simpleTok.txt")), Charset.forName("UTF-8"))) {

      String line;
      while ((line = lines.read()) != null) {
        String[] tags = tagger.tag(WhitespaceTokenizer.INSTANCE.tokenize(line));
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

    evalPosModel(maxentModel, new BigInteger("6912278014292642909634347798602234960"));
  }

  @Test
  public void evalPerceptronModel() throws IOException {
    POSModel perceptronModel = new POSModel(
            new File(EvalUtil.getOpennlpDataDir(), "models-sf/en-pos-perceptron.bin"));

    evalPosModel(perceptronModel, new BigInteger("333081688760132868394207450128996236484"));
  }

  @Test
  public void evalParserModel() throws IOException {

    ParserModel model = new ParserModel(
            new File("/home/burn/opennlp-data-dir", "models-sf/en-parser-chunking.bin"));

    MessageDigest digest = createDigest();


    Parser parser = ParserFactory.create(model);

    try (ObjectStream<String> lines = new PlainTextByLineStream(
            new MarkableFileInputStreamFactory(new File("/home/burn/opennlp-data-dir",
            "leipzig/simpleTok.txt")), Charset.forName("UTF-8"))) {

      String line;
      while ((line = lines.read()) != null) {

        Parse[] parse = ParserTool.parseLine(line, parser, 1);
        if (parse.length > 0) {
          digest.update(parse[0].toString().getBytes("UTF-8"));
        }
        else {
          digest.update("empty".getBytes("UTF-8"));
        }
      }
    }

    Assert.assertEquals(new BigInteger("95566096874728850374427554294889512256"),
            new BigInteger(1, digest.digest()));
  }
}
