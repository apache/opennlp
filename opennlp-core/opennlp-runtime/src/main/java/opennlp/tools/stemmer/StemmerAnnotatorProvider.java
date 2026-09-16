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
package opennlp.tools.stemmer;

import opennlp.tools.document.DocumentAnnotator;
import opennlp.tools.stemmer.snowball.SnowballStemmer;
import opennlp.tools.util.StringUtil;
import opennlp.tools.util.ext.ComponentProvider;
import opennlp.tools.util.ext.ComponentSpec;

/**
 * Creates a {@link StemmerAnnotator} as a {@link DocumentAnnotator} component. The provider
 * name is {@code stemmer}. It supports a request without a location whose only option is
 * {@code algorithm}: absent or {@code porter} selects the {@link PorterStemmer}, any other
 * value names a {@link SnowballStemmer.ALGORITHM}, compared without regard to letter case.
 *
 * @since 3.0.0
 */
public final class StemmerAnnotatorProvider implements ComponentProvider<DocumentAnnotator> {

  /** The option naming the stemming algorithm. */
  public static final String ALGORITHM_OPTION = "algorithm";

  private static final String NAME = "stemmer";
  private static final String PORTER = "porter";

  /** Creates the provider; nothing is built until {@link #create(ComponentSpec)}. */
  public StemmerAnnotatorProvider() {
  }

  /** {@inheritDoc} */
  @Override
  public Class<DocumentAnnotator> type() {
    return DocumentAnnotator.class;
  }

  /** {@inheritDoc} */
  @Override
  public String name() {
    return NAME;
  }

  /**
   * {@inheritDoc}
   * A request without a location whose only option, if any, is a known {@code algorithm}.
   */
  @Override
  public boolean supports(ComponentSpec spec) {
    return spec != null && spec.location() == null && spec.hasOnlyOptions(ALGORITHM_OPTION)
        && algorithm(spec) != null;
  }

  /** {@inheritDoc} */
  @Override
  public DocumentAnnotator create(ComponentSpec spec) {
    if (spec == null) {
      throw new IllegalArgumentException("spec must not be null");
    }
    if (!supports(spec)) {
      throw new IllegalArgumentException("Unsupported stemmer request: " + spec);
    }
    String algorithm = algorithm(spec);
    if (PORTER.equals(algorithm)) {
      return new StemmerAnnotator(new PorterStemmer());
    }
    return new StemmerAnnotator(new SnowballStemmer(SnowballStemmer.ALGORITHM.valueOf(algorithm)));
  }

  /**
   * Resolves the algorithm option to {@code porter} or a {@link SnowballStemmer.ALGORITHM}
   * name, or {@code null} when the value names neither.
   */
  private static String algorithm(ComponentSpec spec) {
    String value = spec.option(ALGORITHM_OPTION, PORTER);
    if (value.isBlank()) {
      return null;
    }
    String lower = StringUtil.toLowerCase(value);
    if (PORTER.equals(lower)) {
      return PORTER;
    }
    String upper = StringUtil.toUpperCase(value);
    for (SnowballStemmer.ALGORITHM candidate : SnowballStemmer.ALGORITHM.values()) {
      if (candidate.name().equals(upper)) {
        return upper;
      }
    }
    return null;
  }
}
