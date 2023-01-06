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

package opennlp.tools.formats.ad;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;

import opennlp.tools.commons.Sample;
import opennlp.tools.formats.AbstractFormatTest;
import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.util.InputStreamFactory;

public abstract class AbstractADSampleStreamTest<T extends Sample> extends AbstractFormatTest {
  protected static final int NUM_SENTENCES = 8;

  protected final List<T> samples = new ArrayList<>();

  protected InputStreamFactory in;

  @BeforeEach
  void setup() throws IOException {
    in = new ResourceAsStreamFactory(AbstractADSampleStreamTest.class, FORMATS_BASE_DIR + "ad.sample");
  }
}
