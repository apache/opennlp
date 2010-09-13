/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.eval.CrossValidationPartitioner.TrainingSampleStream;

import org.junit.Test;

/**
 * Test for the {@link CrossValidationPartitioner} class.
 */
public class CrossValidationPartitionerTest {

  @Test
  public void testEmptyDataSet() throws IOException {
    Collection<String> emptyCollection = Collections.emptySet();
    
    CrossValidationPartitioner<String> partitioner = 
        new CrossValidationPartitioner<String>(emptyCollection, 2);
    
    assertTrue(partitioner.hasNext());
    assertNull(partitioner.next().read());
    
    assertTrue(partitioner.hasNext());
    assertNull(partitioner.next().read());
    
    assertFalse(partitioner.hasNext());
    
    try {
      // Should throw NoSuchElementException
      partitioner.next();
      
      // ups, hasn't thrown one
      fail();
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
    List<String> data = new LinkedList<String>();
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
    
    CrossValidationPartitioner<String> partitioner = new CrossValidationPartitioner<String>(data, 3);
    
    // first partition
    assertTrue(partitioner.hasNext());
    TrainingSampleStream<String> firstTraining = partitioner.next();
    
    assertEquals("02", firstTraining.read());
    assertEquals("03", firstTraining.read());
    assertEquals("05", firstTraining.read());
    assertEquals("06", firstTraining.read());
    assertEquals("08", firstTraining.read());
    assertEquals("09", firstTraining.read());
    assertNull(firstTraining.read());
    
    ObjectStream<String> firstTest = firstTraining.getTestSampleStream();
    
    assertEquals("01", firstTest.read());
    assertEquals("04", firstTest.read());
    assertEquals("07", firstTest.read());
    assertEquals("10", firstTest.read());
    assertNull(firstTest.read());
    
    // second partition
    assertTrue(partitioner.hasNext());
    TrainingSampleStream<String> secondTraining = partitioner.next();
    
    assertEquals("01", secondTraining.read());
    assertEquals("03", secondTraining.read());
    assertEquals("04", secondTraining.read());
    assertEquals("06", secondTraining.read());
    assertEquals("07", secondTraining.read());
    assertEquals("09", secondTraining.read());
    assertEquals("10", secondTraining.read());
    
    assertNull(secondTraining.read());
    
    ObjectStream<String> secondTest = secondTraining.getTestSampleStream();

    assertEquals("02", secondTest.read());
    assertEquals("05", secondTest.read());
    assertEquals("08", secondTest.read());
    assertNull(secondTest.read());
    
    // third partition
    assertTrue(partitioner.hasNext());
    TrainingSampleStream<String> thirdTraining = partitioner.next();
    
    assertEquals("01", thirdTraining.read());
    assertEquals("02", thirdTraining.read());
    assertEquals("04", thirdTraining.read());
    assertEquals("05", thirdTraining.read());
    assertEquals("07", thirdTraining.read());
    assertEquals("08", thirdTraining.read());
    assertEquals("10", thirdTraining.read());
    assertNull(thirdTraining.read());
    
    ObjectStream<String> thirdTest = thirdTraining.getTestSampleStream();
    
    assertEquals("03", thirdTest.read());
    assertEquals("06", thirdTest.read());
    assertEquals("09", thirdTest.read());
    assertNull(thirdTest.read());
    
    assertFalse(partitioner.hasNext());
  }

  @Test
  public void testFailSafty() throws IOException {
    List<String> data = new LinkedList<String>();
    data.add("01");
    data.add("02");
    data.add("03");
    data.add("04");
    
    CrossValidationPartitioner<String> partitioner = new CrossValidationPartitioner<String>(data, 4);
    
    // Test that iterator from previous partition fails
    // if it is accessed
    TrainingSampleStream<String> firstTraining = partitioner.next();
    assertEquals("02", firstTraining.read());
    
    TrainingSampleStream<String> secondTraining = partitioner.next();
    
    try {
      firstTraining.read();
      fail();
    }
    catch (IllegalStateException e) {}

    try {
      firstTraining.getTestSampleStream();
      fail();
    }
    catch (IllegalStateException e) {}
    
    // Test that training iterator fails if there is a test iterator
    secondTraining.getTestSampleStream();
    
    try {
      secondTraining.read();
      fail();
    }
    catch (IllegalStateException e) {}
    
    // Test that test iterator from previous partition fails
    // if there is a new partition
    TrainingSampleStream<String> thirdTraining = partitioner.next();
    ObjectStream<String> thridTest = thirdTraining.getTestSampleStream();
    
    assertTrue(partitioner.hasNext());
    partitioner.next();
    
    try {
      thridTest.read();
      fail();
    }
    catch (IllegalStateException e) {}
  }
  
  @Test
  public void testToString() {
    Collection<String> emptyCollection = Collections.emptySet();
    new CrossValidationPartitioner<String>(emptyCollection, 10).toString();
  }
}
