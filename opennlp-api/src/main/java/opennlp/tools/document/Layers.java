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
 * @since 3.0.0
 */
public final class Layers {

  /**
   * Sentence boundaries; each annotation covers one sentence and carries its text.
   */
  public static final LayerKey<String> SENTENCES = LayerKey.of("sentences", String.class);

  /**
   * Token boundaries; each annotation covers one token and carries its text.
   */
  public static final LayerKey<String> TOKENS = LayerKey.of("tokens", String.class);

  /**
   * Part-of-speech tags; one annotation per token, aligned with {@link #TOKENS} by
   * position, carrying the tag.
   */
  public static final LayerKey<String> POS_TAGS = LayerKey.of("pos", String.class);

  /**
   * Named entities; each annotation covers one mention and carries the entity type.
   */
  public static final LayerKey<String> ENTITIES = LayerKey.of("entities", String.class);

  private Layers() {
    // constants only
  }
}
