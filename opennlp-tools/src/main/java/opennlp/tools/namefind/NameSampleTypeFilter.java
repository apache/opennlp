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

package opennlp.tools.namefind;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;

/**
 * A stream which removes Name Samples which do not have a certain type.
 */
public class NameSampleTypeFilter extends FilterObjectStream<NameSample, NameSample> {

  private final Set<String> types;

  public NameSampleTypeFilter(String[] types, ObjectStream<NameSample> samples) {
    super(samples);
    this.types = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(types)));
  }

  public NameSampleTypeFilter(Set<String> types, ObjectStream<NameSample> samples) {
    super(samples);
    this.types = Collections.unmodifiableSet(new HashSet<>(types));
  }

  public NameSample read() throws IOException {

    NameSample sample = samples.read();

    if (sample != null) {

      List<Span> filteredNames = new ArrayList<>();

      for (Span name : sample.getNames()) {
        if (types.contains(name.getType())) {
          filteredNames.add(name);
        }
      }

      return new NameSample(sample.getId(), sample.getSentence(),
          filteredNames.toArray(new Span[filteredNames.size()]), null, sample.isClearAdaptiveDataSet());
    }
    else {
      return null;
    }
  }
}
