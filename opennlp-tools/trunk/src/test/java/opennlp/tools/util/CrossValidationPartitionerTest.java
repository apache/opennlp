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
 

package opennlp.tools.util;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

import opennlp.tools.util.CrossValidationPartitioner.TrainingIterator;

import junit.framework.TestCase;

/**
 * Test for the {@link CrossValidationPartitioner} class.
 */
public class CrossValidationPartitionerTest extends TestCase {

  public void testEmptyDataSet() {
    Collection<String> emptyCollection = Collections.emptySet();
    
    CrossValidationPartitioner<String> partitioner = 
        new CrossValidationPartitioner<String>(emptyCollection, 2);
    
    assertTrue(partitioner.hasNext());
    assertFalse(partitioner.next().hasNext());
    
    assertTrue(partitioner.hasNext());
    assertFalse(partitioner.next().hasNext());
    
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
  public void test3FoldCV() {
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
    TrainingIterator<String> firstTraining = partitioner.next();
    assertTrue(firstTraining.hasNext());
    assertEquals("02", firstTraining.next());

    assertTrue(firstTraining.hasNext());
    assertEquals("03", firstTraining.next());
    
    assertTrue(firstTraining.hasNext());
    assertEquals("05", firstTraining.next());
    
    assertTrue(firstTraining.hasNext());
    assertEquals("06", firstTraining.next());

    assertTrue(firstTraining.hasNext());
    assertEquals("08", firstTraining.next());
    
    assertTrue(firstTraining.hasNext());
    assertEquals("09", firstTraining.next());
    
    assertFalse(firstTraining.hasNext());
    
    Iterator<String> firstTest = firstTraining.getTestIterator();
    
    assertTrue(firstTest.hasNext());
    assertEquals("01", firstTest.next());
    
    assertTrue(firstTest.hasNext());
    assertEquals("04", firstTest.next());
    
    assertTrue(firstTest.hasNext());
    assertEquals("07", firstTest.next());
    
    assertTrue(firstTest.hasNext());
    assertEquals("10", firstTest.next());
    
    assertFalse(firstTest.hasNext());
    
    // second partition
    assertTrue(partitioner.hasNext());
    TrainingIterator<String> secondTraining = partitioner.next();
    
    assertTrue(secondTraining.hasNext());
    assertEquals("01", secondTraining.next());

    assertTrue(secondTraining.hasNext());
    assertEquals("03", secondTraining.next());
    
    assertTrue(secondTraining.hasNext());
    assertEquals("04", secondTraining.next());
    
    assertTrue(secondTraining.hasNext());
    assertEquals("06", secondTraining.next());
    
    assertTrue(secondTraining.hasNext());
    assertEquals("07", secondTraining.next());
    
    assertTrue(secondTraining.hasNext());
    assertEquals("09", secondTraining.next());
    
    assertTrue(secondTraining.hasNext());
    assertEquals("10", secondTraining.next());
    
    assertFalse(secondTraining.hasNext());
    
    Iterator<String> secondTest = secondTraining.getTestIterator();

    assertTrue(secondTest.hasNext());
    assertEquals("02", secondTest.next());
    
    assertTrue(secondTest.hasNext());
    assertEquals("05", secondTest.next());
    
    assertTrue(secondTest.hasNext());
    assertEquals("08", secondTest.next());
    
    assertFalse(secondTest.hasNext());
    
    // third partition
    assertTrue(partitioner.hasNext());
    TrainingIterator<String> thirdTraining = partitioner.next();
    
    assertTrue(thirdTraining.hasNext());
    assertEquals("01", thirdTraining.next());

    assertTrue(thirdTraining.hasNext());
    assertEquals("02", thirdTraining.next());
    
    assertTrue(thirdTraining.hasNext());
    assertEquals("04", thirdTraining.next());
    
    assertTrue(thirdTraining.hasNext());
    assertEquals("05", thirdTraining.next());
    
    assertTrue(thirdTraining.hasNext());
    assertEquals("07", thirdTraining.next());
    
    assertTrue(thirdTraining.hasNext());
    assertEquals("08", thirdTraining.next());
    
    assertTrue(thirdTraining.hasNext());
    assertEquals("10", thirdTraining.next());
    
    assertFalse(thirdTraining.hasNext());
    
    Iterator<String> thirdTest = thirdTraining.getTestIterator();
    
    assertTrue(thirdTest.hasNext());
    assertEquals("03", thirdTest.next());
    
    assertTrue(thirdTest.hasNext());
    assertEquals("06", thirdTest.next());
    
    assertTrue(thirdTest.hasNext());
    assertEquals("09", thirdTest.next());
    
    assertFalse(thirdTest.hasNext());
    
    assertFalse(partitioner.hasNext());
  }

  public void testFailSafty() {
    List<String> data = new LinkedList<String>();
    data.add("01");
    data.add("02");
    data.add("03");
    data.add("04");
    
    CrossValidationPartitioner<String> partitioner = new CrossValidationPartitioner<String>(data, 4);
    
    // Test that iterator from previous partition fails
    // if it is accessed
    TrainingIterator<String> firstTraining = partitioner.next();
    assertTrue(firstTraining.hasNext());
    assertEquals("02", firstTraining.next());
    
    TrainingIterator<String> secondTraining = partitioner.next();
    
    try {
      firstTraining.hasNext();
      fail();
    }
    catch (IllegalStateException e) {}
    
    try {
      firstTraining.next();
      fail();
    }
    catch (IllegalStateException e) {}

    try {
      firstTraining.getTestIterator();
      fail();
    }
    catch (IllegalStateException e) {}
    
    // Test that training iterator fails if there is a test iterator
    secondTraining.getTestIterator();
    
    try {
      secondTraining.hasNext();
      fail();
    }
    catch (IllegalStateException e) {}
    
    // Test that test iterator from previous partition fails
    // if there is a new partition
    TrainingIterator<String> thirdTraining = partitioner.next();
    Iterator<String> thridTest = thirdTraining.getTestIterator();
    
    assertTrue(partitioner.hasNext());
    partitioner.next();
    
    try {
      thridTest.hasNext();
      fail();
    }
    catch (IllegalStateException e) {}
    
    try {
      thridTest.next();
      fail();
    }
    catch (IllegalStateException e) {}
  }
  
  public void testToString() {
    Collection<String> emptyCollection = Collections.emptySet();
    new CrossValidationPartitioner<String>(emptyCollection, 10).toString();
  }
}
