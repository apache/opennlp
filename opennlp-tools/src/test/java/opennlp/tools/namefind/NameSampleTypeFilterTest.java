/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.namefind;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;

public class NameSampleTypeFilterTest {

  private static NameSampleTypeFilter filter;

  private static final String text = "<START:organization> NATO <END> Secretary - General " +
      "<START:person> Anders Fogh Rasmussen <END> made clear that despite an intensifying " +
      "insurgency and uncertainty over whether <START:location> U . S . <END> President " +
      "<START:person> Barack Obama <END> will send more troops , <START:location> NATO <END> " +
      "will remain in <START:location> Afghanistan <END> .";

  private static final String person = "person";
  private static final String organization = "organization";

  @Test
  public void testNoFilter() throws IOException {

    final String[] types = new String[] {};

    filter = new NameSampleTypeFilter(types, sampleStream(text));

    NameSample ns = filter.read();

    Assert.assertEquals(0, ns.getNames().length);

  }

  @Test
  public void testSingleFilter() throws IOException {

    final String[] types = new String[] {organization};

    filter = new NameSampleTypeFilter(types, sampleStream(text));

    NameSample ns = filter.read();

    Assert.assertEquals(1, ns.getNames().length);
    Assert.assertEquals(organization, ns.getNames()[0].getType());

  }

  @Test
  public void testMultiFilter() throws IOException {

    final String[] types = new String[] {person, organization};

    filter = new NameSampleTypeFilter(types, sampleStream(text));

    NameSample ns = filter.read();

    Map<String, List<Span>> collect = Arrays.stream(ns.getNames())
        .collect(Collectors.groupingBy(Span::getType));
    Assert.assertEquals(2, collect.size());
    Assert.assertEquals(2, collect.get(person).size());
    Assert.assertEquals(1, collect.get(organization).size());

  }

  private ObjectStream<NameSample> sampleStream(String sampleText) throws IOException {

    InputStreamFactory in = () -> new ByteArrayInputStream(sampleText.getBytes(StandardCharsets.UTF_8));

    return new NameSampleDataStream(
        new PlainTextByLineStream(in, StandardCharsets.UTF_8));

  }

}
