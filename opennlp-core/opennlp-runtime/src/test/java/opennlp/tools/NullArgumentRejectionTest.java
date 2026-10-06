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
package opennlp.tools;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.dictionary.serializer.Attributes;
import opennlp.tools.doccat.BagOfWordsFeatureGenerator;
import opennlp.tools.doccat.NGramFeatureGenerator;
import opennlp.tools.entitylinker.EntityLinkerFactory;
import opennlp.tools.log.LogPrintStream;
import opennlp.tools.ml.maxent.GISModel;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.Context;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.SequenceClassificationModel;
import opennlp.tools.namefind.DefaultNameContextGenerator;
import opennlp.tools.namefind.DictionaryNameFinder;
import opennlp.tools.namefind.RegexNameFinder;
import opennlp.tools.namefind.RegexNameFinderFactory;
import opennlp.tools.parser.ParserModel;
import opennlp.tools.parser.ParserType;
import opennlp.tools.postag.ConfigurablePOSContextGenerator;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTagFormatMapper;
import opennlp.tools.tokenize.TokenSampleStream;
import opennlp.tools.tokenize.uax29.WordSegmenter;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.StringList;
import opennlp.tools.util.featuregen.AdaptiveFeatureGenerator;
import opennlp.tools.util.featuregen.AggregatedFeatureGenerator;
import opennlp.tools.util.featuregen.InSpanGenerator;
import opennlp.tools.util.model.GenericModelSerializer;
import opennlp.tools.util.model.ModelUtil;
import opennlp.tools.util.normalizer.AccentFoldCharSequenceNormalizer;
import opennlp.tools.util.normalizer.CaseFoldCharSequenceNormalizer;
import opennlp.tools.util.normalizer.TermAnalyzer;
import opennlp.tools.util.normalizer.TextNormalizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins that the public opennlp-runtime boundaries reject a {@code null} argument with an
 * {@link IllegalArgumentException} naming the parameter, instead of a {@link NullPointerException}.
 */
public class NullArgumentRejectionTest {

  private static final AdaptiveFeatureGenerator NO_FEATURES = (features, tokens, index, previous) -> {
  };

  private static final AbstractModel EMPTY_MODEL =
      new GISModel(new Context[0], new String[0], new String[0]);

  private static Arguments rejects(String label, String parameter, Executable call) {
    return Arguments.of(label, parameter, call);
  }

  static Stream<Arguments> nullArguments() {
    final DictionaryNameFinder finder = new DictionaryNameFinder(new Dictionary(), "type");
    return Stream.of(
        rejects("TextNormalizer aligned normalize", "text",
            () -> TextNormalizer.builder().buildAligned().normalize(null)),
        rejects("TextNormalizer aligned normalize with steps", "text",
            () -> TextNormalizer.builder().whitespace().buildAligned().normalize(null)),
        rejects("TextNormalizer aligned normalizeAligned", "text",
            () -> TextNormalizer.builder().buildAligned().normalizeAligned(null)),
        rejects("TextNormalizer aligned normalizeAligned with steps", "text",
            () -> TextNormalizer.builder().whitespace().buildAligned().normalizeAligned(null)),
        rejects("WordSegmenter.forEachSegment text", "text",
            () -> WordSegmenter.forEachSegment(null, (start, end) -> { })),
        rejects("WordSegmenter.forEachSegment consumer", "consumer",
            () -> WordSegmenter.forEachSegment("", null)),
        rejects("WordSegmenter.boundaries", "text", () -> WordSegmenter.boundaries(null)),
        rejects("WordSegmenter.segments", "text", () -> WordSegmenter.segments(null)),
        rejects("AccentFoldCharSequenceNormalizer", "foldScripts",
            () -> new AccentFoldCharSequenceNormalizer(null, true)),
        rejects("CaseFoldCharSequenceNormalizer", "locale",
            () -> new CaseFoldCharSequenceNormalizer((Locale) null)),
        rejects("CaseFoldCharSequenceNormalizer.getInstance", "locale",
            () -> CaseFoldCharSequenceNormalizer.getInstance((Locale) null)),
        rejects("StringList", "tokens", () -> new StringList(true, (String[]) null)),
        rejects("TokenSampleStream samples", "samples",
            () -> new TokenSampleStream(null, "<SPLIT>")),
        rejects("TokenSampleStream separatorChars", "separatorChars",
            () -> new TokenSampleStream(ObjectStreamUtils.createObjectStream("a"), null)),
        rejects("AggregatedFeatureGenerator array", "generators",
            () -> new AggregatedFeatureGenerator((AdaptiveFeatureGenerator[]) null)),
        rejects("AggregatedFeatureGenerator collection", "generators",
            () -> new AggregatedFeatureGenerator((Collection<AdaptiveFeatureGenerator>) null)),
        rejects("InSpanGenerator prefix", "prefix", () -> new InSpanGenerator(null, finder)),
        rejects("InSpanGenerator finder", "finder", () -> new InSpanGenerator("p", null)),
        rejects("GenericModelSerializer artifact", "artifact",
            () -> new GenericModelSerializer().serialize(null, new ByteArrayOutputStream())),
        rejects("GenericModelSerializer out", "out",
            () -> new GenericModelSerializer().serialize(EMPTY_MODEL, null)),
        rejects("ModelUtil.writeModel model", "model",
            () -> ModelUtil.writeModel(null, new ByteArrayOutputStream())),
        rejects("ModelUtil.writeModel out", "out",
            () -> ModelUtil.writeModel(EMPTY_MODEL, null)),
        rejects("RegexNameFinder", "regexMap", () -> new RegexNameFinder(null)),
        rejects("DictionaryNameFinder dictionary", "dictionary",
            () -> new DictionaryNameFinder(null, "type")),
        rejects("DictionaryNameFinder type", "type",
            () -> new DictionaryNameFinder(new Dictionary(), null)),
        rejects("DefaultNameContextGenerator", "featureGenerators",
            () -> new DefaultNameContextGenerator((AdaptiveFeatureGenerator[]) null)),
        rejects("RegexNameFinderFactory config", "config",
            () -> RegexNameFinderFactory.getDefaultRegexNameFinders(
                (Map<String, Pattern[]>) null)),
        rejects("RegexNameFinderFactory defaults", "defaults",
            () -> RegexNameFinderFactory.getDefaultRegexNameFinders(
                (RegexNameFinderFactory.DEFAULT_REGEX_NAME_FINDER[]) null)),
        rejects("EntityLinkerFactory.getLinker", "properties",
            () -> EntityLinkerFactory.getLinker(null)),
        rejects("ConfigurablePOSContextGenerator", "featureGenerator",
            () -> new ConfigurablePOSContextGenerator(null)),
        rejects("POSModel from stream", "in", () -> new POSModel((InputStream) null)),
        rejects("POSModel from file", "modelFile", () -> new POSModel((File) null)),
        rejects("POSModel from path", "modelPath", () -> new POSModel((Path) null)),
        rejects("POSModel from url", "modelURL", () -> new POSModel((URL) null)),
        rejects("ParserModel TREEINSERT attachModel", "attachModel",
            () -> new ParserModel("eng", null, null, null, null, null, null, ParserType.TREEINSERT)),
        rejects("LogPrintStream logger", "logger", () -> new LogPrintStream(null)),
        rejects("LogPrintStream logger with level", "logger",
            () -> new LogPrintStream(null, Level.INFO)),
        rejects("LogPrintStream level", "level",
            () -> new LogPrintStream(LoggerFactory.getLogger(NullArgumentRejectionTest.class), null)),
        rejects("POSModel languageCode", "languageCode",
            () -> new POSModel(null, (SequenceClassificationModel) null, null, null)),
        rejects("POSModel maxent posModel", "posModel",
            () -> new POSModel("eng", (MaxentModel) null, 1, null, null)),
        rejects("POSTagFormatMapper.guessFormat", "posModel",
            () -> POSTagFormatMapper.guessFormat(null)),
        rejects("POSTagFormatMapper.convertTags", "tags",
            () -> new POSTagFormatMapper(new String[0]) { }.convertTags(null)),
        rejects("POSTagFormatMapper.NoOp.convertTags", "tags",
            () -> new POSTagFormatMapper.NoOp() { }.convertTags(null)),
        rejects("Attributes.setValue key", "key", () -> new Attributes().setValue(null, "v")),
        rejects("Attributes.setValue value", "value", () -> new Attributes().setValue("k", null)),
        rejects("NGramFeatureGenerator.extractFeatures", "text",
            () -> new NGramFeatureGenerator().extractFeatures(null, Collections.emptyMap())),
        rejects("BagOfWordsFeatureGenerator.extractFeatures", "text",
            () -> new BagOfWordsFeatureGenerator().extractFeatures(null, Collections.emptyMap()))
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("nullArguments")
  void testNullArgumentIsRejected(String label, String parameter, Executable call) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class, call);
    assertEquals(parameter + " must not be null", e.getMessage());
  }

  @Test
  void testNullElementsAreRejected() {
    final Set<Character.UnicodeScript> scripts = new HashSet<>();
    scripts.add(null);
    assertThrows(IllegalArgumentException.class,
        () -> new AccentFoldCharSequenceNormalizer(scripts, true));
    assertThrows(IllegalArgumentException.class,
        () -> TermAnalyzer.builder().accentFold(scripts, true));
    assertThrows(IllegalArgumentException.class,
        () -> new AggregatedFeatureGenerator(NO_FEATURES, null));
    assertThrows(IllegalArgumentException.class,
        () -> new AggregatedFeatureGenerator(Collections.singletonList(null)));
  }

  @Test
  void testEmptyAlignedPipelineStillAcceptsText() {
    assertEquals("", TextNormalizer.builder().buildAligned().normalize("").toString());
    assertEquals(List.of(), WordSegmenter.segments(""));
  }
}
