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

import java.util.List;

import opennlp.tools.document.DocumentAnnotator;
import opennlp.tools.document.DocumentAnnotatorProvider;
import opennlp.tools.stemmer.snowball.SnowballStemmer;
import opennlp.tools.util.ext.ProviderSpec;

/**
 * Provides a {@link StemmerAnnotator} under the name {@value #NAME}. It supports a spec without
 * location whose only option is {@value #ALGORITHM_OPTION}: {@value #PORTER} or absent for the
 * {@link PorterStemmer}, otherwise the name of a {@link SnowballStemmer.ALGORITHM}. Values are
 * compared ignoring case, so {@value #PORTER} always means the {@link PorterStemmer} and the
 * Snowball algorithm of that name is not reachable through this provider.
 *
 * @since 3.0.0
 */
public final class StemmerAnnotatorProvider implements DocumentAnnotatorProvider {

  /** The name of this provider. */
  public static final String NAME = "stemmer";

  /** The option that names the stemming algorithm. */
  public static final String ALGORITHM_OPTION = "algorithm";

  /** The {@value #ALGORITHM_OPTION} value for the {@link PorterStemmer}. */
  public static final String PORTER = "porter";

  private final List<SnowballStemmer.ALGORITHM> algorithms =
      List.of(SnowballStemmer.ALGORITHM.values());

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public boolean supports(final ProviderSpec spec) {
    if (spec == null) {
      throw new IllegalArgumentException("spec must not be null");
    }
    return spec.location().isEmpty() && spec.hasOnlyOptions(ALGORITHM_OPTION)
        && isKnown(spec.option(ALGORITHM_OPTION, PORTER));
  }

  private boolean isKnown(final String algorithm) {
    return PORTER.equalsIgnoreCase(algorithm) || snowballAlgorithm(algorithm) != null;
  }

  @Override
  public DocumentAnnotator create(final ProviderSpec spec) {
    if (spec == null) {
      throw new IllegalArgumentException("spec must not be null");
    }
    if (!supports(spec)) {
      throw new IllegalArgumentException("Unsupported spec: " + spec);
    }
    final String algorithm = spec.option(ALGORITHM_OPTION, PORTER);
    if (PORTER.equalsIgnoreCase(algorithm)) {
      return new StemmerAnnotator(new PorterStemmer());
    }
    final SnowballStemmer.ALGORITHM snowball = snowballAlgorithm(algorithm);
    if (snowball == null) {
      throw new IllegalArgumentException("Unsupported spec: " + spec);
    }
    return new StemmerAnnotator(new SnowballStemmer(snowball));
  }

  private SnowballStemmer.ALGORITHM snowballAlgorithm(final String algorithm) {
    for (final SnowballStemmer.ALGORITHM candidate : algorithms) {
      if (candidate.name().equalsIgnoreCase(algorithm)) {
        return candidate;
      }
    }
    return null;
  }
}
