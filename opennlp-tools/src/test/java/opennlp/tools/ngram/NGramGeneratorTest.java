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

package opennlp.tools.ngram;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class NGramGeneratorTest {
  
  @Test
  public void generateListTest() {
    
    final List<String> input = Arrays.asList("This", "is", "a", "sentence");
    final int window = 2;
    final String separator = "-";
    
    final List<String> ngrams = NGramGenerator.generate(input, window, separator);
    
    Assert.assertEquals(3,  ngrams.size());
    Assert.assertTrue(ngrams.contains("This-is"));
    Assert.assertTrue(ngrams.contains("is-a"));
    Assert.assertTrue(ngrams.contains("a-sentence"));
    
  }
  
  @Test
  public void generateCharTest() {
    
    final char[] input = "Test again".toCharArray();
    final int window = 4;
    final String separator = "-";
    
    final List<String> ngrams = NGramGenerator.generate(input, window, separator);

    Assert.assertEquals(7,  ngrams.size());
    Assert.assertTrue(ngrams.contains("T-e-s-t"));
    Assert.assertTrue(ngrams.contains("e-s-t- "));
    Assert.assertTrue(ngrams.contains("s-t- -a"));
    Assert.assertTrue(ngrams.contains("t- -a-g"));
    Assert.assertTrue(ngrams.contains(" -a-g-a"));
    Assert.assertTrue(ngrams.contains("a-g-a-i"));
    Assert.assertTrue(ngrams.contains("g-a-i-n"));
    
  }
  
  @Test
  public void generateLargerWindowThanListTest() {
    
    final List<String> input = Arrays.asList("One", "two");
    final int window = 3;
    final String separator = "-";
    
    final List<String> ngrams = NGramGenerator.generate(input, window, separator);
    
    Assert.assertTrue(ngrams.isEmpty());
    
  }
  
  @Test
  public void emptyTest() {
    
    final List<String> input = new ArrayList<>();
    final int window = 2;
    final String separator = "-";
    
    final List<String> ngrams = NGramGenerator.generate(input, window, separator);

    Assert.assertTrue(ngrams.isEmpty());
    
  }
  
}
