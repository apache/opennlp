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

package opennlp.tools.util.eval;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.eval.CrossValidationPartitioner.TrainingSampleStream;

/**
 * Test for the {@link CrossValidationPartitioner} class.
 */
public class CrossValidationPartitionerTest {

  @Test
  void testEmptyDataSet() throws IOException {
    Collection<String> emptyCollection = Collections.emptySet();

    CrossValidationPartitioner<String> partitioner =
        new CrossValidationPartitioner<>(emptyCollection, 2);

    Assertions.assertTrue(partitioner.hasNext());
    Assertions.assertNull(partitioner.next().read());

    Assertions.assertTrue(partitioner.hasNext());
    Assertions.assertNull(partitioner.next().read());

    Assertions.assertFalse(partitioner.hasNext());

    try {
      // Should throw NoSuchElementException
      partitioner.next();

      // ups, hasn't thrown one
      Assertions.fail();
    } catch (NoSuchElementException e) {
      // expected
    }
  }

  /**
   * Test 3-fold cross validation on a small sample data set.
   */
  @Test
  void test3FoldCV() throws IOException {
    List<String> data = new LinkedList<>();
    data.add("01");
    data.add("02");
    data.add("03");
    data.add("04");
    data.add("05");
    data.add("06");
    data.add("07");
    data.add("08");
    data.add("09");
    data.add("10");

    CrossValidationPartitioner<String> partitioner = new CrossValidationPartitioner<>(data, 3);

    // first partition
    Assertions.assertTrue(partitioner.hasNext());
    TrainingSampleStream<String> firstTraining = partitioner.next();

    Assertions.assertEquals("02", firstTraining.read());
    Assertions.assertEquals("03", firstTraining.read());
    Assertions.assertEquals("05", firstTraining.read());
    Assertions.assertEquals("06", firstTraining.read());
    Assertions.assertEquals("08", firstTraining.read());
    Assertions.assertEquals("09", firstTraining.read());
    Assertions.assertNull(firstTraining.read());

    ObjectStream<String> firstTest = firstTraining.getTestSampleStream();

    Assertions.assertEquals("01", firstTest.read());
    Assertions.assertEquals("04", firstTest.read());
    Assertions.assertEquals("07", firstTest.read());
    Assertions.assertEquals("10", firstTest.read());
    Assertions.assertNull(firstTest.read());

    // second partition
    Assertions.assertTrue(partitioner.hasNext());
    TrainingSampleStream<String> secondTraining = partitioner.next();

    Assertions.assertEquals("01", secondTraining.read());
    Assertions.assertEquals("03", secondTraining.read());
    Assertions.assertEquals("04", secondTraining.read());
    Assertions.assertEquals("06", secondTraining.read());
    Assertions.assertEquals("07", secondTraining.read());
    Assertions.assertEquals("09", secondTraining.read());
    Assertions.assertEquals("10", secondTraining.read());

    Assertions.assertNull(secondTraining.read());

    ObjectStream<String> secondTest = secondTraining.getTestSampleStream();

    Assertions.assertEquals("02", secondTest.read());
    Assertions.assertEquals("05", secondTest.read());
    Assertions.assertEquals("08", secondTest.read());
    Assertions.assertNull(secondTest.read());

    // third partition
    Assertions.assertTrue(partitioner.hasNext());
    TrainingSampleStream<String> thirdTraining = partitioner.next();

    Assertions.assertEquals("01", thirdTraining.read());
    Assertions.assertEquals("02", thirdTraining.read());
    Assertions.assertEquals("04", thirdTraining.read());
    Assertions.assertEquals("05", thirdTraining.read());
    Assertions.assertEquals("07", thirdTraining.read());
    Assertions.assertEquals("08", thirdTraining.read());
    Assertions.assertEquals("10", thirdTraining.read());
    Assertions.assertNull(thirdTraining.read());

    ObjectStream<String> thirdTest = thirdTraining.getTestSampleStream();

    Assertions.assertEquals("03", thirdTest.read());
    Assertions.assertEquals("06", thirdTest.read());
    Assertions.assertEquals("09", thirdTest.read());
    Assertions.assertNull(thirdTest.read());

    Assertions.assertFalse(partitioner.hasNext());
  }

  @Test
  void testFailSafty() throws IOException {
    List<String> data = new LinkedList<>();
    data.add("01");
    data.add("02");
    data.add("03");
    data.add("04");

    CrossValidationPartitioner<String> partitioner =
        new CrossValidationPartitioner<>(data, 4);

    // Test that iterator from previous partition fails
    // if it is accessed
    TrainingSampleStream<String> firstTraining = partitioner.next();
    Assertions.assertEquals("02", firstTraining.read());

    TrainingSampleStream<String> secondTraining = partitioner.next();

    try {
      firstTraining.read();
      Assertions.fail();
    } catch (IllegalStateException expected) {
      // the read above is expected to throw an exception
    }

    try {
      firstTraining.getTestSampleStream();
      Assertions.fail();
    } catch (IllegalStateException expected) {
      // the read above is expected to throw an exception
    }

    // Test that training iterator fails if there is a test iterator
    secondTraining.getTestSampleStream();

    try {
      secondTraining.read();
      Assertions.fail();
    } catch (IllegalStateException expected) {
      // the read above is expected to throw an exception
    }

    // Test that test iterator from previous partition fails
    // if there is a new partition
    TrainingSampleStream<String> thirdTraining = partitioner.next();
    ObjectStream<String> thridTest = thirdTraining.getTestSampleStream();

    Assertions.assertTrue(partitioner.hasNext());
    partitioner.next();

    try {
      thridTest.read();
      Assertions.fail();
    } catch (IllegalStateException expected) {
      // the read above is expected to throw an exception
    }
  }

  @Test
  void testToString() {
    Collection<String> emptyCollection = Collections.emptySet();
    new CrossValidationPartitioner<>(emptyCollection, 10).toString();
  }
}
