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

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.eval.CrossValidationPartitioner.TrainingSampleStream;

/**
 * Test for the {@link CrossValidationPartitioner} class.
 */
public class CrossValidationPartitionerTest {

  @Test
  public void testEmptyDataSet() throws IOException {
    Collection<String> emptyCollection = Collections.emptySet();

    CrossValidationPartitioner<String> partitioner =
        new CrossValidationPartitioner<>(emptyCollection, 2);

    Assert.assertTrue(partitioner.hasNext());
    Assert.assertNull(partitioner.next().read());

    Assert.assertTrue(partitioner.hasNext());
    Assert.assertNull(partitioner.next().read());

    Assert.assertFalse(partitioner.hasNext());

    try {
      // Should throw NoSuchElementException
      partitioner.next();

      // ups, hasn't thrown one
      Assert.fail();
    }
    catch (NoSuchElementException e) {
      // expected
    }
  }

  /**
   * Test 3-fold cross validation on a small sample data set.
   */
  @Test
  public void test3FoldCV() throws IOException {
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
    Assert.assertTrue(partitioner.hasNext());
    TrainingSampleStream<String> firstTraining = partitioner.next();

    Assert.assertEquals("02", firstTraining.read());
    Assert.assertEquals("03", firstTraining.read());
    Assert.assertEquals("05", firstTraining.read());
    Assert.assertEquals("06", firstTraining.read());
    Assert.assertEquals("08", firstTraining.read());
    Assert.assertEquals("09", firstTraining.read());
    Assert.assertNull(firstTraining.read());

    ObjectStream<String> firstTest = firstTraining.getTestSampleStream();

    Assert.assertEquals("01", firstTest.read());
    Assert.assertEquals("04", firstTest.read());
    Assert.assertEquals("07", firstTest.read());
    Assert.assertEquals("10", firstTest.read());
    Assert.assertNull(firstTest.read());

    // second partition
    Assert.assertTrue(partitioner.hasNext());
    TrainingSampleStream<String> secondTraining = partitioner.next();

    Assert.assertEquals("01", secondTraining.read());
    Assert.assertEquals("03", secondTraining.read());
    Assert.assertEquals("04", secondTraining.read());
    Assert.assertEquals("06", secondTraining.read());
    Assert.assertEquals("07", secondTraining.read());
    Assert.assertEquals("09", secondTraining.read());
    Assert.assertEquals("10", secondTraining.read());

    Assert.assertNull(secondTraining.read());

    ObjectStream<String> secondTest = secondTraining.getTestSampleStream();

    Assert.assertEquals("02", secondTest.read());
    Assert.assertEquals("05", secondTest.read());
    Assert.assertEquals("08", secondTest.read());
    Assert.assertNull(secondTest.read());

    // third partition
    Assert.assertTrue(partitioner.hasNext());
    TrainingSampleStream<String> thirdTraining = partitioner.next();

    Assert.assertEquals("01", thirdTraining.read());
    Assert.assertEquals("02", thirdTraining.read());
    Assert.assertEquals("04", thirdTraining.read());
    Assert.assertEquals("05", thirdTraining.read());
    Assert.assertEquals("07", thirdTraining.read());
    Assert.assertEquals("08", thirdTraining.read());
    Assert.assertEquals("10", thirdTraining.read());
    Assert.assertNull(thirdTraining.read());

    ObjectStream<String> thirdTest = thirdTraining.getTestSampleStream();

    Assert.assertEquals("03", thirdTest.read());
    Assert.assertEquals("06", thirdTest.read());
    Assert.assertEquals("09", thirdTest.read());
    Assert.assertNull(thirdTest.read());

    Assert.assertFalse(partitioner.hasNext());
  }

  @Test
  public void testFailSafty() throws IOException {
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
    Assert.assertEquals("02", firstTraining.read());

    TrainingSampleStream<String> secondTraining = partitioner.next();

    try {
      firstTraining.read();
      Assert.fail();
    }
    catch (IllegalStateException expected) {
      // the read above is expected to throw an exception
    }

    try {
      firstTraining.getTestSampleStream();
      Assert.fail();
    }
    catch (IllegalStateException expected) {
      // the read above is expected to throw an exception
    }

    // Test that training iterator fails if there is a test iterator
    secondTraining.getTestSampleStream();

    try {
      secondTraining.read();
      Assert.fail();
    }
    catch (IllegalStateException expected) {
      // the read above is expected to throw an exception
    }

    // Test that test iterator from previous partition fails
    // if there is a new partition
    TrainingSampleStream<String> thirdTraining = partitioner.next();
    ObjectStream<String> thridTest = thirdTraining.getTestSampleStream();

    Assert.assertTrue(partitioner.hasNext());
    partitioner.next();

    try {
      thridTest.read();
      Assert.fail();
    }
    catch (IllegalStateException expected) {
      // the read above is expected to throw an exception
    }
  }

  @Test
  public void testToString() {
    Collection<String> emptyCollection = Collections.emptySet();
    new CrossValidationPartitioner<>(emptyCollection, 10).toString();
  }
}
