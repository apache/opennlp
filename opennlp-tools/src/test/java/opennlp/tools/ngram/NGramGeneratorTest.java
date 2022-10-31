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
  public void generateListTest1() {

    final List<String> input = Arrays.asList("This", "is", "a", "sentence");
    final int window = 1;
    final String separator = "-";

    final List<String> ngrams = NGramGenerator.generate(input, window, separator);

    Assert.assertEquals(4,  ngrams.size());
    Assert.assertEquals("This", ngrams.get(0));
    Assert.assertEquals("is", ngrams.get(1));
    Assert.assertEquals("a", ngrams.get(2));
    Assert.assertEquals("sentence", ngrams.get(3));

  }

  @Test
  public void generateListTest2() {

    final List<String> input = Arrays.asList("This", "is", "a", "sentence");
    final int window = 2;
    final String separator = "-";

    final List<String> ngrams = NGramGenerator.generate(input, window, separator);

    Assert.assertEquals(3,  ngrams.size());
    Assert.assertEquals("This-is", ngrams.get(0));
    Assert.assertEquals("is-a", ngrams.get(1));
    Assert.assertEquals("a-sentence", ngrams.get(2));

  }

  @Test
  public void generateListTest3() {

    final List<String> input = Arrays.asList("This", "is", "a", "sentence");
    final int window = 3;
    final String separator = "-";

    final List<String> ngrams = NGramGenerator.generate(input, window, separator);

    Assert.assertEquals(2, ngrams.size());
    Assert.assertEquals("This-is-a", ngrams.get(0));
    Assert.assertEquals("is-a-sentence", ngrams.get(1));

  }

  @Test
  public void generateListTest4() {

    final List<String> input = Arrays.asList("This", "is", "a", "sentence");
    final int window = 4;
    final String separator = "-";

    final List<String> ngrams = NGramGenerator.generate(input, window, separator);

    Assert.assertEquals(1, ngrams.size());
    Assert.assertEquals("This-is-a-sentence", ngrams.get(0));

  }

  @Test
  public void generateCharTest1() {

    final char[] input = "Test".toCharArray();
    final int window = 1;
    final String separator = "-";

    final List<String> ngrams = NGramGenerator.generate(input, window, separator);

    Assert.assertEquals(4, ngrams.size());
    Assert.assertEquals("T", ngrams.get(0));
    Assert.assertEquals("e", ngrams.get(1));
    Assert.assertEquals("s", ngrams.get(2));
    Assert.assertEquals("t", ngrams.get(3));

  }

  @Test
  public void generateCharTest2() {

    final char[] input = "Test".toCharArray();
    final int window = 2;
    final String separator = "-";

    final List<String> ngrams = NGramGenerator.generate(input, window, separator);

    Assert.assertEquals(3,  ngrams.size());
    Assert.assertEquals("T-e", ngrams.get(0));
    Assert.assertEquals("e-s", ngrams.get(1));
    Assert.assertEquals("s-t", ngrams.get(2));

  }

  @Test
  public void generateCharTest3() {

    final char[] input = "Test".toCharArray();
    final int window = 3;
    final String separator = "-";

    final List<String> ngrams = NGramGenerator.generate(input, window, separator);

    Assert.assertEquals(2,  ngrams.size());
    Assert.assertEquals("T-e-s", ngrams.get(0));
    Assert.assertEquals("e-s-t", ngrams.get(1));

  }

  @Test
  public void generateCharTest4() {

    final char[] input = "Test".toCharArray();
    final int window = 4;
    final String separator = "-";

    final List<String> ngrams = NGramGenerator.generate(input, window, separator);

    Assert.assertEquals(1,  ngrams.size());
    Assert.assertEquals("T-e-s-t", ngrams.get(0));

  }

  @Test
  public void generateCharTest() {

    final char[] input = "Test again".toCharArray();
    final int window = 4;
    final String separator = "-";

    final List<String> ngrams = NGramGenerator.generate(input, window, separator);

    Assert.assertEquals(7,  ngrams.size());
    Assert.assertEquals(("T-e-s-t"), ngrams.get(0));
    Assert.assertEquals(("e-s-t- "), ngrams.get(1));
    Assert.assertEquals(("s-t- -a"), ngrams.get(2));
    Assert.assertEquals(("t- -a-g"), ngrams.get(3));
    Assert.assertEquals((" -a-g-a"), ngrams.get(4));
    Assert.assertEquals(("a-g-a-i"), ngrams.get(5));
    Assert.assertEquals(("g-a-i-n"), ngrams.get(6));

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
