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

package opennlp.tools.monitoring;

import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import opennlp.tools.util.TrainingParameters;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultTrainingProgressMonitorTest {

  private static final String LOGGER_NAME = "opennlp";
  private static final Logger logger = (Logger) LoggerFactory.getLogger(LOGGER_NAME);
  private static final Level originalLogLevel  = logger != null ? logger.getLevel() : Level.OFF;

  private TrainingProgressMonitor progressMonitor;
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();


  @BeforeAll
  static void beforeAll() {
    logger.setLevel(Level.INFO);
  }

  @BeforeEach
  public void setup() {
    progressMonitor = new DefaultTrainingProgressMonitor();
    appender.list.clear();
    logger.addAppender(appender);
    appender.start();
  }

  @Test
  void testFinishedIteration() {
    progressMonitor.finishedIteration(1, 19830, 20801, TrainingMeasure.ACCURACY, 0.953319551944618);
    progressMonitor.finishedIteration(2, 19852, 20801, TrainingMeasure.ACCURACY, 0.9543771934041633);
    progressMonitor.display(true);

    //Assert that two logging events are captured for two iterations.
    List<String> actual = appender.list.stream().map(ILoggingEvent::getMessage).
        collect(toList());
    List<String> expected = List.of("1: (19830/20801) Training Accuracy : 0.953319551944618",
        "2: (19852/20801) Training Accuracy : 0.9543771934041633");
    assertArrayEquals(expected.toArray(), actual.toArray());

  }

  @Test
  void testFinishedTrainingWithStopCriteria() {
    StopCriteria<Double> stopCriteria = new IterDeltaAccuracyUnderTolerance(
            new TrainingParameters(Map.of("Tolerance", .00002)));
    progressMonitor.finishedTraining(150, stopCriteria);
    progressMonitor.display(true);

    //Assert that the logs captured the training completion message with StopCriteria satisfied.
    List<String> actual = appender.list.stream().map(ILoggingEvent::getMessage).
            collect(toList());
    List<String> expected = List.of("Stopping: change in training set accuracy less than {2.0E-5}");
    assertArrayEquals(expected.toArray(), actual.toArray());
  }

  @Test
  void testFinishedTrainingWithoutStopCriteria() {
    progressMonitor.finishedTraining(150, null);
    progressMonitor.display(true);

    //Assert that the logs captured the training completion message when all iterations are exhausted.
    List<String> actual = appender.list.stream().map(ILoggingEvent::getMessage).
            collect(toList());
    List<String> expected = List.of("Training Finished after completing 150 Iterations successfully.");
    assertArrayEquals(expected.toArray(), actual.toArray());
  }

  @Test
  void displayAndClear() {
    progressMonitor.finishedTraining(150, null);
    progressMonitor.display(true);

    //Assert that the previous invocation of display has cleared the recorded training progress.
    appender.list.clear();
    progressMonitor.display(true);
    assertEquals(0, appender.list.size());
  }

  @Test
  void displayAndKeep() {
    progressMonitor.finishedTraining(150, null);
    progressMonitor.display(false);

    //Assert that the previous invocation of display has not cleared the recorded training progress.
    progressMonitor.display(false);
    assertEquals(2, appender.list.size());
  }

  @AfterAll
  static void afterAll() {
    logger.setLevel(originalLogLevel);
  }
}
