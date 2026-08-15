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
package opennlp.tools.wordnet;

/**
 * The four parts of speech a wordnet-style lexicon distinguishes.
 *
 * <p>The enum carries none of the single-letter codes the on-disk formats use; readers own the
 * mapping from their format's codes to these values. Adjective satellites normalize to
 * {@link #ADJECTIVE}, with the cluster structure preserved through
 * {@link WordNetRelation#SIMILAR_TO}.</p>
 */
public enum WordNetPOS {

  /** Nouns. */
  NOUN,

  /** Verbs. */
  VERB,

  /** Adjectives, including adjective satellites. */
  ADJECTIVE,

  /** Adverbs. */
  ADVERB
}
