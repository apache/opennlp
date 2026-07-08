/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.stemmer;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.stemmer.snowball.SnowballStemmer;
import opennlp.tools.stemmer.snowball.SnowballStemmerFactory;

class StemmerFactoryTest {

  @Test
  void snowballFactoryMatchesDirectStemmer() {
    StemmerFactory factory = new SnowballStemmerFactory(SnowballStemmer.ALGORITHM.ENGLISH);
    Stemmer direct = new SnowballStemmer(SnowballStemmer.ALGORITHM.ENGLISH);
    Assertions.assertEquals(direct.stem("running"), factory.newStemmer().stem("running"));
    Assertions.assertEquals(direct.stem("running"), factory.stem("running"));
  }

  @Test
  void porterFactoryMatchesDirectStemmer() {
    StemmerFactory factory = new PorterStemmerFactory();
    Stemmer direct = new PorterStemmer();
    Assertions.assertEquals(direct.stem("running"), factory.newStemmer().stem("running"));
  }

  @Test
  void stemAllDefaultReturnsSingleForm() {
    StemmerFactory factory = new SnowballStemmerFactory(SnowballStemmer.ALGORITHM.ENGLISH);
    List<CharSequence> forms = factory.newStemmer().stemAll("running");
    Assertions.assertEquals(1, forms.size());
    Assertions.assertEquals("run", forms.getFirst());
  }

  @Test
  void factoryRejectsInvalidRepeat() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new SnowballStemmerFactory(SnowballStemmer.ALGORITHM.ENGLISH, 0));
  }

  @Test
  void sharingStemmerMatchesSingleThreadReference() throws Exception {
    StemmerFactory factory = new SnowballStemmerFactory(SnowballStemmer.ALGORITHM.ENGLISH);
    Stemmer reference = factory.newStemmer();
    SharingStemmer shared = new SharingStemmer(factory);

    Assertions.assertEquals(reference.stem("running"), shared.stem("running"));
    Assertions.assertEquals(reference.stem("this"), shared.stem("this"));
  }

  @Test
  void sharingStemmerIsSafeUnderConcurrentUse() throws Exception {
    StemmerFactory factory = new SnowballStemmerFactory(SnowballStemmer.ALGORITHM.ENGLISH);
    SharingStemmer shared = new SharingStemmer(factory);
    CharSequence expectedRunning = factory.newStemmer().stem("running");
    CharSequence expectedDeclining = factory.newStemmer().stem("declining");

    try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
      Future<?>[] tasks = new Future<?>[64];
      for (int i = 0; i < tasks.length; i++) {
        tasks[i] = pool.submit(() -> {
          for (int n = 0; n < 200; n++) {
            Assertions.assertEquals(expectedRunning, shared.stem("running"));
            Assertions.assertEquals(expectedDeclining, shared.stem("declining"));
          }
        });
      }
      for (Future<?> task : tasks) {
        task.get(30, TimeUnit.SECONDS);
      }
    }
  }

  @Test
  void snowballStemmerIsSafeUnderConcurrentUse() throws Exception {
    Stemmer shared = new SnowballStemmer(SnowballStemmer.ALGORITHM.ENGLISH);
    CharSequence expectedRunning = shared.stem("running");
    CharSequence expectedDeclining = shared.stem("declining");

    try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
      Future<?>[] tasks = new Future<?>[64];
      for (int i = 0; i < tasks.length; i++) {
        tasks[i] = pool.submit(() -> {
          for (int n = 0; n < 200; n++) {
            Assertions.assertEquals(expectedRunning, shared.stem("running"));
            Assertions.assertEquals(expectedDeclining, shared.stem("declining"));
          }
        });
      }
      for (Future<?> task : tasks) {
        task.get(30, TimeUnit.SECONDS);
      }
    }
  }

  @Test
  void sharingStemmerRejectsNullFactory() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> new SharingStemmer(null));
  }
}
