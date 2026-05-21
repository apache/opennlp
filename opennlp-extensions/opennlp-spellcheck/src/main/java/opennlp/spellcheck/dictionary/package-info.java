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

/**
 * Persistence and model-loading for the SymSpell spell checker (OPENNLP-1832).
 *
 * <p>This package turns plain-text frequency dictionaries into a queryable engine and
 * provides a compact, versioned, serializable model artifact:</p>
 *
 * <ul>
 *   <li>{@link opennlp.spellcheck.dictionary.FrequencyDictionaryLoader} &ndash; loads
 *       {@code word<TAB>count} unigram and {@code w1 w2<TAB>count} bigram dictionaries
 *       (UTF-8 by default, blank/comment-tolerant) into a
 *       {@link opennlp.spellcheck.symspell.SymSpell} engine via
 *       {@link opennlp.tools.util.ObjectStream}.</li>
 *   <li>{@link opennlp.spellcheck.dictionary.SymSpellModel} &ndash; a
 *       {@link opennlp.tools.util.model.SerializableArtifact} wrapping a built engine plus
 *       metadata (language, config) and its source dictionary.</li>
 *   <li>{@link opennlp.spellcheck.dictionary.SymSpellModelSerializer} &ndash; the
 *       {@link opennlp.tools.util.model.ArtifactSerializer} implementing a compact
 *       versioned binary format (source dictionary + config; the delete index is rebuilt
 *       on load).</li>
 *   <li>{@link opennlp.spellcheck.dictionary.SymSpellModels} &ndash; convenience factory
 *       and (de)serialization helpers, including {@code model.properties} emission.</li>
 *   <li>{@link opennlp.spellcheck.dictionary.SymSpellModelResolver} &ndash; resolves a
 *       packaged model by language from the classpath via the OpenNLP model-resolver.</li>
 * </ul>
 */
package opennlp.spellcheck.dictionary;
