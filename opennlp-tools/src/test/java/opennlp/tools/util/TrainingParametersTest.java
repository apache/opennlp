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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.ml.EventTrainer;

public class TrainingParametersTest {

  @Test
  void testConstructors() throws Exception {
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
  void testDefault() {
    TrainingParameters tr = TrainingParameters.defaultParams();

    Assertions.assertEquals(4, tr.getObjectSettings().size());
    Assertions.assertEquals("MAXENT", tr.algorithm());
    Assertions.assertEquals(EventTrainer.EVENT_VALUE,
        tr.getStringParameter(TrainingParameters.TRAINER_TYPE_PARAM,
            "v11"));  // use different defaults
    Assertions.assertEquals(100,
        tr.getIntParameter(TrainingParameters.ITERATIONS_PARAM,
            200));  // use different defaults
    Assertions.assertEquals(5,
        tr.getIntParameter(TrainingParameters.CUTOFF_PARAM,
            200));  // use different defaults
  }

  @Test
  public void testSetParamsWithCLIParams() {
    String[] args =
        { "-model" , "en-token-test.bin" , "-alphaNumOpt" , "isAlphaNumOpt" , "-lang" , "en" , "-data" ,
            "en-token.train" , "-encoding" , "UTF-8" , "-cutoff" , "10" , "-iterations" , "50" };
    TrainingParameters tr = TrainingParameters.setParams(args);

    Assertions.assertEquals("MAXENT" , tr.algorithm());
    Assertions.assertEquals(50 ,
        tr.getIntParameter(TrainingParameters.ITERATIONS_PARAM ,
            TrainingParameters.ITERATIONS_DEFAULT_VALUE));
    Assertions.assertEquals(10 ,
        tr.getIntParameter(TrainingParameters.CUTOFF_PARAM ,
            TrainingParameters.CUTOFF_DEFAULT_VALUE));
  }

  @Test
  public void testSetParamsWithoutCLIParams() {
    String[] args =
        { "-model" , "en-token-test.bin" , "-alphaNumOpt" , "isAlphaNumOpt" , "-lang" , "en" , "-data" ,
            "en-token.train" , "-encoding" , "UTF-8" };
    TrainingParameters tr = TrainingParameters.setParams(args);

    Assertions.assertEquals("MAXENT" , tr.algorithm());
    Assertions.assertEquals(100 ,
        tr.getIntParameter(TrainingParameters.ITERATIONS_PARAM ,
            TrainingParameters.ITERATIONS_DEFAULT_VALUE));
    Assertions.assertEquals(5 ,
        tr.getIntParameter(TrainingParameters.CUTOFF_PARAM ,
            TrainingParameters.CUTOFF_DEFAULT_VALUE));
  }

  @Test
  public void testSetParamsWithoutCutoffCLIParams() {
    String[] args =
        { "-model" , "en-token-test.bin" , "-alphaNumOpt" , "isAlphaNumOpt" , "-lang" , "en" , "-data" ,
            "en-token.train" , "-encoding" , "UTF-8" , "-iterations" , "50" };
    TrainingParameters tr = TrainingParameters.setParams(args);

    Assertions.assertEquals("MAXENT" , tr.algorithm());
    Assertions.assertEquals(50 ,
        tr.getIntParameter(TrainingParameters.ITERATIONS_PARAM ,
            TrainingParameters.ITERATIONS_DEFAULT_VALUE));
    Assertions.assertEquals(5 ,
        tr.getIntParameter(TrainingParameters.CUTOFF_PARAM ,
            TrainingParameters.CUTOFF_DEFAULT_VALUE));
  }

  @Test
  public void testSetParamsWithoutIterationsCLIParams() {
    String[] args =
        { "-model" , "en-token-test.bin" , "-alphaNumOpt" , "isAlphaNumOpt" , "-lang" , "en" , "-data" ,
            "en-token.train" , "-encoding" , "UTF-8" , "-cutoff" , "10" };
    TrainingParameters tr = TrainingParameters.setParams(args);

    Assertions.assertEquals("MAXENT" , tr.algorithm());
    Assertions.assertEquals(100 ,
        tr.getIntParameter(TrainingParameters.ITERATIONS_PARAM ,
            TrainingParameters.ITERATIONS_DEFAULT_VALUE));
    Assertions.assertEquals(10 ,
        tr.getIntParameter(TrainingParameters.CUTOFF_PARAM ,
            TrainingParameters.CUTOFF_DEFAULT_VALUE));
  }

  @Test
  void testGetAlgorithm() {
    TrainingParameters tp = build("Algorithm=Perceptron,n1.Algorithm=SVM");

    Assertions.assertEquals("Perceptron", tp.algorithm());
    Assertions.assertEquals("SVM", tp.algorithm("n1"));
  }

  @Test
  void testGetAlgorithmCaseInsensitive() {
    TrainingParameters tp = build("ALGORITHM=Perceptron,n1.Algorithm=SVM");

    Assertions.assertEquals("Perceptron", tp.algorithm());
    Assertions.assertEquals("SVM", tp.algorithm("n1"));
  }

  @Test
  void testGetSettings() {
    TrainingParameters tp = build("k1=v1,n1.k2=v2,n2.k3=v3,n1.k4=v4");

    assertEquals(buildMap("k1=v1"), tp.getObjectSettings());
    assertEquals(buildMap("k2=v2,k4=v4"), tp.getObjectSettings("n1"));
    assertEquals(buildMap("k3=v3"), tp.getObjectSettings("n2"));
    Assertions.assertTrue(tp.getObjectSettings("n3").isEmpty());
  }

  @Test
  void testGetParameters() {
    TrainingParameters tp = build("k1=v1,n1.k2=v2,n2.k3=v3,n1.k4=v4");

    assertEquals(build("k1=v1"), tp.getParameters(null));
    assertEquals(build("k2=v2,k4=v4"), tp.getParameters("n1"));
    assertEquals(build("k3=v3"), tp.getParameters("n2"));
    Assertions.assertTrue(tp.getParameters("n3").getObjectSettings().isEmpty());
  }

  @Test
  void testPutGet() {
    TrainingParameters tp =
        build("k1=v1,int.k2=123,str.k2=v3,str.k3=v4,boolean.k4=false,double.k5=123.45,k21=234.5");

    Assertions.assertEquals("v1", tp.getStringParameter("k1", "def"));
    Assertions.assertEquals("def", tp.getStringParameter("k2", "def"));
    Assertions.assertEquals("v3", tp.getStringParameter("str", "k2", "def"));
    Assertions.assertEquals("def", tp.getStringParameter("str", "k4", "def"));

    Assertions.assertEquals(-100, tp.getIntParameter("k11", -100));
    tp.put("k11", 234);
    Assertions.assertEquals(234, tp.getIntParameter("k11", -100));
    Assertions.assertEquals(123, tp.getIntParameter("int", "k2", -100));
    Assertions.assertEquals(-100, tp.getIntParameter("int", "k4", -100));

    Assertions.assertEquals(tp.getDoubleParameter("k21", -100), 0.001, 234.5);
    tp.put("k21", 345.6);
    Assertions.assertEquals(tp.getDoubleParameter("k21", -100), 0.001, 345.6); // should be changed
    tp.putIfAbsent("k21", 456.7);
    Assertions.assertEquals(tp.getDoubleParameter("k21", -100), 0.001, 345.6); // should be unchanged
    Assertions.assertEquals(tp.getDoubleParameter("double", "k5", -100), 0.001, 123.45);

    Assertions.assertTrue(tp.getBooleanParameter("k31", true));
    tp.put("k31", false);
    Assertions.assertFalse(tp.getBooleanParameter("k31", true));
    Assertions.assertFalse(tp.getBooleanParameter("boolean", "k4", true));
  }

  // format: k1=v1,k2=v2,...
  private static Map<String, Object> buildMap(String str) {
    String[] pairs = str.split(",");
    Map<String, Object> map = new HashMap<>(pairs.length);
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

  private static void assertEquals(Map<String, Object> map1, Map<String, Object> map2) {
    Assertions.assertNotNull(map1);
    Assertions.assertNotNull(map2);
    Assertions.assertEquals(map1.size(), map2.size());
    for (String key : map1.keySet()) {
      Assertions.assertEquals(map1.get(key), map2.get(key));
    }
  }

  private static void assertEquals(Map<String, Object> map, TrainingParameters actual) {
    Assertions.assertNotNull(actual);
    assertEquals(map, actual.getObjectSettings());
  }

  private static void assertEquals(TrainingParameters expected, TrainingParameters actual) {
    if (expected == null) {
      Assertions.assertNull(actual);
    } else {
      assertEquals(expected.getObjectSettings(), actual);
    }
  }
}
