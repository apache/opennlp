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

package opennlp.tools.postag;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.StringList;

/**
 *
 * We encountered a concurrency issue in the pos tagger module in the class
 * DefaultPOSContextGenerator.

 The issue is demonstrated in DefaultPOSContextGeneratorTest.java. The test "multithreading()"
 consistently fails on our system with the current code if the number of threads
 (NUMBER_OF_THREADS) is set to 10. If the number of threads is set to 1 (effectively disabling
 multithreading), the test consistently passes.

 We resolved the issue by removing a field in DefaultPOSContextGenerator.java.
 *
 */


public class DefaultPOSContextGeneratorTest {


  public static final int NUMBER_OF_THREADS = 10;
  private static Object[] tokens;
  private static DefaultPOSContextGenerator defaultPOSContextGenerator;
  private static String[] tags;

  @BeforeClass
  public static void setUp() {
    final String matchingToken = "tokenC";

    tokens = new Object[] {"tokenA", "tokenB", matchingToken, "tokenD"};

    final StringList stringList = new StringList(new String[] {matchingToken});

    Dictionary dictionary = new Dictionary();
    dictionary.put(stringList);

    defaultPOSContextGenerator = new DefaultPOSContextGenerator(dictionary);

    tags = new String[] {"tagA", "tagB", "tagC", "tagD"};
  }

  @Test
  public void noDictionaryMatch() {
    int index = 1;

    final String[] actual = defaultPOSContextGenerator.getContext(index, tokens, tags);

    final String[] expected = new String[] {
        "default",
        "w=tokenB",
        "suf=B",
        "suf=nB",
        "suf=enB",
        "suf=kenB",
        "pre=t",
        "pre=to",
        "pre=tok",
        "pre=toke",
        "c",
        "p=tokenA",
        "t=tagA",
        "pp=*SB*",
        "n=tokenC",
        "nn=tokenD"
    };

    Assert.assertArrayEquals("Calling with not matching index at: " + index +
        "\nexpected \n" + Arrays.toString(expected) + " but actually was \n"
        + Arrays.toString(actual), expected, actual);
  }

  @Test
  public void dictionaryMatch() {
    int indexWithDictionaryMatch = 2;

    final String[] actual =
        defaultPOSContextGenerator.getContext(indexWithDictionaryMatch, tokens, tags);

    final String[] expected = new String[] {
        "default",
        "w=tokenC",
        "p=tokenB",
        "t=tagB",
        "pp=tokenA",
        "t2=tagA,tagB",
        "n=tokenD",
        "nn=*SE*"
    };

    Assert.assertArrayEquals("Calling with index matching dictionary entry at: "
        + indexWithDictionaryMatch + "\nexpected \n" + Arrays.toString(expected)
        + " but actually was \n" + Arrays.toString(actual), expected, actual);
  }

  @Test
  public void multithreading() {
    Callable<Void> matching = () -> {

      dictionaryMatch();

      return null;
    };

    Callable<Void> notMatching = () -> {

      noDictionaryMatch();

      return null;
    };

    final List<Callable<Void>> callables = IntStream.range(0, 200000)
        .mapToObj(index -> (index % 2 == 0) ? matching : notMatching)
        .collect(Collectors.toList());

    final ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    try {
      final List<Future<Void>> futures = executorService.invokeAll(callables);

      executorService.shutdown();
      executorService.awaitTermination(30, TimeUnit.SECONDS);

      futures.forEach(future -> {

        try {
          future.get();
        } catch (InterruptedException e) {
          Assert.fail("Interrupted because of: " + e.getCause().getMessage());
        } catch (ExecutionException ee) {
          Assert.fail(ee.getCause().getMessage());
        }

      });
    } catch (final InterruptedException e) {
      Assert.fail("Test interrupted");
    }
  }
}
