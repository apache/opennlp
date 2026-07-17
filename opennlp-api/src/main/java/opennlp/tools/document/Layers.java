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

package opennlp.tools.document;

/**
 * The standard {@link LayerKey layer keys} for the results the toolkit produces itself.
 *
 * <p>This class is a convenience, not a registry: the key space stays open, and any
 * producer may define further keys in its own package. New capabilities must never
 * require an addition here to function.</p>
 *
 * <p>Namespace rule: every key the toolkit itself defines carries the
 * {@code opennlp:} id prefix. An extension defines its keys under its own prefix, and
 * a bare id without a prefix is legal for an application-local layer, so ids from
 * independent producers cannot collide.</p>
 *
 * <p>Placement rule: this class holds only the keys of the core linguistic layers
 * every pipeline shares (sentences, tokens, tags, entities). A capability-specific
 * layer's key lives on the annotator that provides it, for example the lemma layer's
 * key on its adapter, so adding a capability never touches this class.</p>
 *
 * @since 3.0.0
 */
public final class Layers {

  /**
   * Sentence boundaries; each annotation covers one sentence and carries its text.
   */
  public static final LayerKey<String> SENTENCES = LayerKey.of("opennlp:sentences", String.class);

  /**
   * Token boundaries; each annotation covers one token and carries its text.
   */
  public static final LayerKey<String> TOKENS = LayerKey.of("opennlp:tokens", String.class);

  /**
   * Part-of-speech tags; one annotation per token, aligned with {@link #TOKENS} by
   * position, carrying the tag.
   */
  public static final LayerKey<String> POS_TAGS = LayerKey.of("opennlp:pos", String.class);

  /**
   * Named entities; each annotation covers one mention and carries the entity type.
   */
  public static final LayerKey<String> ENTITIES = LayerKey.of("opennlp:entities", String.class);

  private Layers() {
    // This class holds constants only and is never instantiated.
  }
}
