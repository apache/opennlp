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

package opennlp.tools.util.ext;

import java.io.UncheckedIOException;

import opennlp.tools.chunker.ChunkerFactory;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.commons.Internal;
import opennlp.tools.doccat.BagOfWordsFeatureGenerator;
import opennlp.tools.doccat.DoccatFactory;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.EmojiFeatureGenerator;
import opennlp.tools.doccat.NGramFeatureGenerator;
import opennlp.tools.langdetect.LanguageDetectorFactory;
import opennlp.tools.langdetect.LanguageDetectorModel;
import opennlp.tools.lemmatizer.LemmatizerFactory;
import opennlp.tools.lemmatizer.LemmatizerModel;
import opennlp.tools.namefind.BilouCodec;
import opennlp.tools.namefind.BioCodec;
import opennlp.tools.namefind.TokenNameFinderFactory;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.parser.ParserChunkerFactory;
import opennlp.tools.parser.ParserModel;
import opennlp.tools.parser.lang.en.HeadRules;
import opennlp.tools.parser.lang.es.AncoraSpanishHeadRules;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerFactory;
import opennlp.tools.sentdetect.SentenceDetectorFactory;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.sentiment.EmojiSentimentFactory;
import opennlp.tools.sentiment.SentimentFactory;
import opennlp.tools.sentiment.SentimentModel;
import opennlp.tools.tokenize.BPEModel;
import opennlp.tools.tokenize.BPETokenizerFactory;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.TokenizerFactory;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.featuregen.AggregatedFeatureGeneratorFactory;
import opennlp.tools.util.featuregen.BigramNameFeatureGeneratorFactory;
import opennlp.tools.util.featuregen.BrownCluster;
import opennlp.tools.util.featuregen.BrownClusterBigramFeatureGeneratorFactory;
import opennlp.tools.util.featuregen.BrownClusterTokenClassFeatureGeneratorFactory;
import opennlp.tools.util.featuregen.BrownClusterTokenFeatureGeneratorFactory;
import opennlp.tools.util.featuregen.CachedFeatureGeneratorFactory;
import opennlp.tools.util.featuregen.CharacterNgramFeatureGeneratorFactory;
import opennlp.tools.util.featuregen.DefinitionFeatureGeneratorFactory;
import opennlp.tools.util.featuregen.DictionaryFeatureGeneratorFactory;
import opennlp.tools.util.featuregen.DocumentBeginFeatureGeneratorFactory;
import opennlp.tools.util.featuregen.EmojiAnnotationFeatureGeneratorFactory;
import opennlp.tools.util.featuregen.POSTaggerNameFeatureGeneratorFactory;
import opennlp.tools.util.featuregen.PosTaggerFeatureGeneratorFactory;
import opennlp.tools.util.featuregen.PrefixFeatureGeneratorFactory;
import opennlp.tools.util.featuregen.PreviousMapFeatureGeneratorFactory;
import opennlp.tools.util.featuregen.SentenceFeatureGeneratorFactory;
import opennlp.tools.util.featuregen.SuffixFeatureGeneratorFactory;
import opennlp.tools.util.featuregen.TokenClassFeatureGeneratorFactory;
import opennlp.tools.util.featuregen.TokenFeatureGeneratorFactory;
import opennlp.tools.util.featuregen.TokenPatternFeatureGeneratorFactory;
import opennlp.tools.util.featuregen.TrigramNameFeatureGeneratorFactory;
import opennlp.tools.util.featuregen.WindowFeatureGeneratorFactory;
import opennlp.tools.util.featuregen.WordClusterDictionary;
import opennlp.tools.util.featuregen.WordClusterFeatureGeneratorFactory;
import opennlp.tools.util.jvm.CHMStringDeduplicator;
import opennlp.tools.util.jvm.CHMStringInterner;
import opennlp.tools.util.jvm.HMStringInterner;
import opennlp.tools.util.jvm.JvmStringInterner;
import opennlp.tools.util.jvm.NoOpStringInterner;
import opennlp.tools.util.model.ByteArraySerializer;
import opennlp.tools.util.model.ChunkerModelSerializer;
import opennlp.tools.util.model.DictionarySerializer;
import opennlp.tools.util.model.GenericModelSerializer;
import opennlp.tools.util.model.ModelLoader;
import opennlp.tools.util.model.POSModelSerializer;

/**
 * Registers the extensions of opennlp-runtime: tool factories, artifact
 * serializers, feature generator factories, sequence codecs, document
 * categorizer feature generators, string interners, tokenizers and model loaders.
 * <p>
 * Registered through {@code META-INF/services}. Do not use this class, internal use only!
 */
@Internal
public class RuntimeExtensionRegistrar implements ExtensionRegistrar {

  @Override
  public void register(ExtensionRegistry registry) {
    registerToolFactories(registry);
    registerArtifactSerializers(registry);
    registerFeatureGeneratorFactories(registry);
    registerSequenceCodecs(registry);
    registerDoccatFeatureGenerators(registry);
    registerStringInterners(registry);
    registerTokenizers(registry);
    registerModelLoaders(registry);
  }

  private void registerToolFactories(ExtensionRegistry registry) {
    registry.register(ChunkerFactory.class, ChunkerFactory::new);
    registry.register(ParserChunkerFactory.class, ParserChunkerFactory::new);
    registry.register(DoccatFactory.class, DoccatFactory::new);
    registry.register(LanguageDetectorFactory.class, LanguageDetectorFactory::new);
    registry.register(LemmatizerFactory.class, LemmatizerFactory::new);
    registry.register(TokenNameFinderFactory.class, TokenNameFinderFactory::new);
    registry.register(POSTaggerFactory.class, POSTaggerFactory::new);
    registry.register(SentenceDetectorFactory.class, SentenceDetectorFactory::new);
    registry.register(TokenizerFactory.class, TokenizerFactory::new);
    registry.register(BPETokenizerFactory.class, BPETokenizerFactory::new);
    registry.register(SentimentFactory.class, SentimentFactory::new);
    registry.register(EmojiSentimentFactory.class, EmojiSentimentFactory::new);
  }

  private void registerArtifactSerializers(ExtensionRegistry registry) {
    registry.register(HeadRules.HeadRulesSerializer.class, HeadRules.HeadRulesSerializer::new);
    registry.register(AncoraSpanishHeadRules.HeadRulesSerializer.class,
        AncoraSpanishHeadRules.HeadRulesSerializer::new);
    registry.register(POSTaggerFactory.POSDictionarySerializer.class,
        POSTaggerFactory.POSDictionarySerializer::new);
    registry.register(POSModelSerializer.class, POSModelSerializer::new);
    registry.register(BrownCluster.BrownClusterSerializer.class, BrownCluster.BrownClusterSerializer::new);
    registry.register(WordClusterDictionary.WordClusterDictionarySerializer.class,
        WordClusterDictionary.WordClusterDictionarySerializer::new);
    registry.register(ByteArraySerializer.class, ByteArraySerializer::new);
    registry.register(DictionarySerializer.class, DictionarySerializer::new);
    registry.register(GenericModelSerializer.class, GenericModelSerializer::new);
    registry.register(ChunkerModelSerializer.class, ChunkerModelSerializer::new);
  }

  private void registerFeatureGeneratorFactories(ExtensionRegistry registry) {
    registry.register(AggregatedFeatureGeneratorFactory.class, AggregatedFeatureGeneratorFactory::new);
    registry.register(BigramNameFeatureGeneratorFactory.class, BigramNameFeatureGeneratorFactory::new);
    registry.register(BrownClusterBigramFeatureGeneratorFactory.class,
        BrownClusterBigramFeatureGeneratorFactory::new);
    registry.register(BrownClusterTokenClassFeatureGeneratorFactory.class,
        BrownClusterTokenClassFeatureGeneratorFactory::new);
    registry.register(BrownClusterTokenFeatureGeneratorFactory.class,
        BrownClusterTokenFeatureGeneratorFactory::new);
    registry.register(CachedFeatureGeneratorFactory.class, CachedFeatureGeneratorFactory::new);
    registry.register(CharacterNgramFeatureGeneratorFactory.class,
        CharacterNgramFeatureGeneratorFactory::new);
    registry.register(DefinitionFeatureGeneratorFactory.class, DefinitionFeatureGeneratorFactory::new);
    registry.register(DictionaryFeatureGeneratorFactory.class, DictionaryFeatureGeneratorFactory::new);
    registry.register(DocumentBeginFeatureGeneratorFactory.class, DocumentBeginFeatureGeneratorFactory::new);
    registry.register(EmojiAnnotationFeatureGeneratorFactory.class,
        EmojiAnnotationFeatureGeneratorFactory::new);
    registry.register(POSTaggerNameFeatureGeneratorFactory.class, POSTaggerNameFeatureGeneratorFactory::new);
    registry.register(PosTaggerFeatureGeneratorFactory.class, PosTaggerFeatureGeneratorFactory::new);
    registry.register(PrefixFeatureGeneratorFactory.class, PrefixFeatureGeneratorFactory::new);
    registry.register(PreviousMapFeatureGeneratorFactory.class, PreviousMapFeatureGeneratorFactory::new);
    registry.register(SentenceFeatureGeneratorFactory.class, SentenceFeatureGeneratorFactory::new);
    registry.register(SuffixFeatureGeneratorFactory.class, SuffixFeatureGeneratorFactory::new);
    registry.register(TokenClassFeatureGeneratorFactory.class, TokenClassFeatureGeneratorFactory::new);
    registry.register(TokenFeatureGeneratorFactory.class, TokenFeatureGeneratorFactory::new);
    registry.register(TokenPatternFeatureGeneratorFactory.class, TokenPatternFeatureGeneratorFactory::new);
    registry.register(TrigramNameFeatureGeneratorFactory.class, TrigramNameFeatureGeneratorFactory::new);
    registry.register(WindowFeatureGeneratorFactory.class, WindowFeatureGeneratorFactory::new);
    registry.register(WordClusterFeatureGeneratorFactory.class, WordClusterFeatureGeneratorFactory::new);
  }

  private void registerSequenceCodecs(ExtensionRegistry registry) {
    registry.register(BioCodec.class, BioCodec::new);
    registry.register(BilouCodec.class, BilouCodec::new);
  }

  private void registerDoccatFeatureGenerators(ExtensionRegistry registry) {
    registry.register(BagOfWordsFeatureGenerator.class, BagOfWordsFeatureGenerator::new);
    registry.register(NGramFeatureGenerator.class, () -> {
      try {
        return new NGramFeatureGenerator();
      } catch (InvalidFormatException e) {
        // the no-arg constructor validates constant defaults, so this cannot happen
        throw new UncheckedIOException(e);
      }
    });
    registry.register(EmojiFeatureGenerator.class, EmojiFeatureGenerator::new);
  }

  private void registerStringInterners(ExtensionRegistry registry) {
    registry.register(CHMStringInterner.class, CHMStringInterner::new);
    registry.register(CHMStringDeduplicator.class, CHMStringDeduplicator::new);
    registry.register(HMStringInterner.class, HMStringInterner::new);
    registry.register(JvmStringInterner.class, JvmStringInterner::new);
    registry.register(NoOpStringInterner.class, NoOpStringInterner::new);
  }

  private void registerTokenizers(ExtensionRegistry registry) {
    registry.register(SimpleTokenizer.class, () -> SimpleTokenizer.INSTANCE);
    registry.register(WhitespaceTokenizer.class, () -> WhitespaceTokenizer.INSTANCE);
  }

  private void registerModelLoaders(ExtensionRegistry registry) {
    registry.register(BPEModel.class, ModelLoader.class, (ModelLoader<BPEModel>) BPEModel::new);
    registry.register(ChunkerModel.class, ModelLoader.class, (ModelLoader<ChunkerModel>) ChunkerModel::new);
    registry.register(DoccatModel.class, ModelLoader.class, (ModelLoader<DoccatModel>) DoccatModel::new);
    registry.register(LanguageDetectorModel.class, ModelLoader.class,
        (ModelLoader<LanguageDetectorModel>) LanguageDetectorModel::new);
    registry.register(LemmatizerModel.class, ModelLoader.class,
        (ModelLoader<LemmatizerModel>) LemmatizerModel::new);
    registry.register(ParserModel.class, ModelLoader.class, (ModelLoader<ParserModel>) ParserModel::new);
    registry.register(POSModel.class, ModelLoader.class, (ModelLoader<POSModel>) POSModel::new);
    registry.register(SentenceModel.class, ModelLoader.class,
        (ModelLoader<SentenceModel>) SentenceModel::new);
    registry.register(SentimentModel.class, ModelLoader.class,
        (ModelLoader<SentimentModel>) SentimentModel::new);
    registry.register(TokenNameFinderModel.class, ModelLoader.class,
        (ModelLoader<TokenNameFinderModel>) TokenNameFinderModel::new);
    registry.register(TokenizerModel.class, ModelLoader.class,
        (ModelLoader<TokenizerModel>) TokenizerModel::new);
  }
}
