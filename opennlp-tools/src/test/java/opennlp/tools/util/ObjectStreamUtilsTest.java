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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Assert;
import org.junit.Test;

public class ObjectStreamUtilsTest {

  @Test
  public void buildStreamTest() throws IOException {
    String[] data = {"dog","cat","pig","frog"};
    
    // make a stream out of the data array...
    ObjectStream<String> stream = ObjectStreamUtils.createObjectStream(data);
    compare(stream, data);
    
    // make a stream out of a list...
    List<String> dataList = Arrays.asList(data);
    stream = ObjectStreamUtils.createObjectStream(Arrays.asList(data));
    compare(stream, data);
    
    // make a stream out of a set...
    // A treeSet will order the set in Alphabetical order, so
    // we can compare it with the sorted Array, but this changes the 
    // array.  so it must be checked last.
    Arrays.sort(data);
    stream = ObjectStreamUtils.createObjectStream(new TreeSet<>(dataList));
    compare(stream, data);
  }

  @Test
  public void concatenateStreamTest() throws IOException {
    String[] data1 = {"dog1","cat1","pig1","frog1"};
    String[] data2 = {"dog2","cat2","pig2","frog2"};
    String[] expected = {"dog1","cat1","pig1","frog1","dog2","cat2","pig2","frog2"};

    // take individual streams and concatenate them as 1 stream.
    // Note: this is much easier than trying to create an array of
    // streams which needs to have annotation to avoid warnings about
    // generics and arrays.
    ObjectStream<String> stream = ObjectStreamUtils.concatenateObjectStream(
        ObjectStreamUtils.createObjectStream(data1),
        ObjectStreamUtils.createObjectStream(data2));
    compare(stream, expected);

    // test that collections of streams can be concatenated...
    List<ObjectStream<String>> listOfStreams = new ArrayList<>();
    listOfStreams.add(ObjectStreamUtils.createObjectStream(data1) );
    listOfStreams.add(ObjectStreamUtils.createObjectStream(data2) );
    stream = ObjectStreamUtils.concatenateObjectStream(listOfStreams);
    compare(stream, expected);

    
    // test that sets of streams can be concatenated..
    Set<ObjectStream<String>> streamSet = new HashSet<>();
    streamSet.add(ObjectStreamUtils.createObjectStream(data1) );
    streamSet.add(ObjectStreamUtils.createObjectStream(data2) );
    stream = ObjectStreamUtils.concatenateObjectStream(streamSet);
    // The order the of the streams in the set is not know a priori
    // just check that the dog, cat, pig. frog is in the write order...
    compareUpToLastCharacter(stream, expected);
    
  }
  
  
  
  private void compare(ObjectStream<String> stream,String[] expectedValues) throws IOException {
    String value = "";
    int i = 0;
    while ( (value = stream.read()) != null) {
      Assert.assertTrue("The stream is longer than expected at index: " + i +
          " expected length: " + expectedValues.length +
          " expectedValues" + Arrays.toString(expectedValues),i < expectedValues.length);
      Assert.assertEquals(expectedValues[i++], value);
    }
  }
  
  private void compareUpToLastCharacter(ObjectStream<String> stream,
      String[] expectedValues) throws IOException {
    
    String value = "";
    int i = 0;
    while ( (value = stream.read()) != null) {
      Assert.assertTrue("The stream is longer than expected at index: " + i + 
          " expected length: " + expectedValues.length +
          " expectedValues" + Arrays.toString(expectedValues),i < expectedValues.length);
      Assert.assertEquals(
          expectedValues[i].substring(0, expectedValues[i].length() - 1), 
          value.substring(0, value.length() - 1));
      i++;
    }
  }
  
}
