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

package opennlp.tools.util;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.ml.EventTrainer;

public class TrainingParametersTest {

  @Test
  public void testConstructors() throws Exception {
    TrainingParameters tp1 =
        new TrainingParameters(build("key1=val1,key2=val2,key3=val3"));

    TrainingParameters tp2 = new TrainingParameters(
        new ByteArrayInputStream("key1=val1\nkey2=val2\nkey3=val3\n".getBytes())
    );

    TrainingParameters tp3 = new TrainingParameters(tp2);

    assertEquals(tp1, tp2);
    assertEquals(tp2, tp3);
  }

  @Test
  public void testDefault() {
    TrainingParameters tr = TrainingParameters.defaultParams();

    Assert.assertEquals(4, tr.getSettings().size());
    Assert.assertEquals("MAXENT", tr.algorithm());
    Assert.assertEquals(EventTrainer.EVENT_VALUE,
        tr.getStringParameter(TrainingParameters.TRAINER_TYPE_PARAM,
            "v11"));  // use different defaults
    Assert.assertEquals(100,
        tr.getIntParameter(TrainingParameters.ITERATIONS_PARAM,
            200));  // use different defaults
    Assert.assertEquals(5,
        tr.getIntParameter(TrainingParameters.CUTOFF_PARAM,
            200));  // use different defaults
  }

  @Test
  public void testGetAlgorithm() {
    TrainingParameters tp = build("Algorithm=Perceptron,n1.Algorithm=SVM");

    Assert.assertEquals("Perceptron", tp.algorithm());
    Assert.assertEquals("SVM", tp.algorithm("n1"));
  }

  @Test
  public void testGetSettings() {
    TrainingParameters tp = build("k1=v1,n1.k2=v2,n2.k3=v3,n1.k4=v4");

    assertEquals(buildMap("k1=v1"), tp.getSettings());
    assertEquals(buildMap("k2=v2,k4=v4"), tp.getSettings("n1"));
    assertEquals(buildMap("k3=v3"), tp.getSettings("n2"));
    Assert.assertTrue(tp.getSettings("n3").isEmpty());
  }

  @Test
  public void testGetParameters() {
    TrainingParameters tp = build("k1=v1,n1.k2=v2,n2.k3=v3,n1.k4=v4");

    assertEquals(build("k1=v1"), tp.getParameters(null));
    assertEquals(build("k2=v2,k4=v4"), tp.getParameters("n1"));
    assertEquals(build("k3=v3"), tp.getParameters("n2"));
    Assert.assertTrue(tp.getParameters("n3").getSettings().isEmpty());
  }

  @Test
  public void testPutGet() {
    TrainingParameters tp =
        build("k1=v1,int.k2=123,str.k2=v3,str.k3=v4,boolean.k4=false,double.k5=123.45,k21=234.5");

    Assert.assertEquals("v1", tp.getStringParameter("k1", "def"));
    Assert.assertEquals("def", tp.getStringParameter("k2", "def"));
    Assert.assertEquals("v3", tp.getStringParameter("str", "k2", "def"));
    Assert.assertEquals("def", tp.getStringParameter("str", "k4", "def"));

    Assert.assertEquals(-100, tp.getIntParameter("k11", -100));
    tp.put("k11", 234);
    Assert.assertEquals(234, tp.getIntParameter("k11", -100));
    Assert.assertEquals(123, tp.getIntParameter("int", "k2", -100));
    Assert.assertEquals(-100, tp.getIntParameter("int", "k4", -100));

    Assert.assertEquals(234.5, tp.getDoubleParameter("k21", -100), 0.001);
    tp.put("k21", 345.6);
    Assert.assertEquals(345.6, tp.getDoubleParameter("k21", -100), 0.001); // should be changed
    tp.putIfAbsent("k21", 456.7);
    Assert.assertEquals(345.6, tp.getDoubleParameter("k21", -100), 0.001); // should be unchanged
    Assert.assertEquals(123.45, tp.getDoubleParameter("double", "k5", -100), 0.001);

    Assert.assertEquals(true, tp.getBooleanParameter("k31", true));
    tp.put("k31", false);
    Assert.assertEquals(false, tp.getBooleanParameter("k31", true));
    Assert.assertEquals(false, tp.getBooleanParameter("boolean", "k4", true));
  }

  // format: k1=v1,k2=v2,...
  private static Map<String, String> buildMap(String str) {
    String[] pairs = str.split(",");
    Map<String, String> map = new HashMap<>(pairs.length);
    for (String pair : pairs) {
      String[] keyValue = pair.split("=");
      map.put(keyValue[0], keyValue[1]);
    }

    return map;
  }

  // format: k1=v1,k2=v2,...
  private static TrainingParameters build(String str) {
    return new TrainingParameters(buildMap(str));
  }

  private static void assertEquals(Map<String, String> map1, Map<String, String> map2) {
    Assert.assertNotNull(map1);
    Assert.assertNotNull(map2);
    Assert.assertEquals(map1.size(), map2.size());
    for (String key : map1.keySet()) {
      Assert.assertEquals(map1.get(key), map2.get(key));
    }
  }

  private static void assertEquals(Map<String, String> map, TrainingParameters actual) {
    Assert.assertNotNull(actual);
    assertEquals(map, actual.getSettings());
  }

  private static void assertEquals(TrainingParameters expected, TrainingParameters actual) {
    if (expected == null) {
      Assert.assertNull(actual);
    } else {
      assertEquals(expected.getSettings(), actual);
    }
  }
}
