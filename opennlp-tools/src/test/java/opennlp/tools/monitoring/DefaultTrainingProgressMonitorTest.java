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
import java.util.stream.Collectors;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import opennlp.tools.util.TrainingParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultTrainingProgressMonitorTest {

  private static final String LOGGER_NAME = "opennlp";
  private TrainingProgressMonitor progressMonitor;
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private static final Logger logger = (Logger) LoggerFactory.getLogger(LOGGER_NAME);
  private static final Level originalLogLevel  = logger != null ? logger.getLevel() : Level.OFF;

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
    progressMonitor.displayAndClear();

    //Assert that two logging events are captured for two iterations.
    Assertions.assertThat(appender.list.stream().map(ILoggingEvent::getMessage).
        collect(Collectors.toList())).
        isEqualTo(List.of("1: (19830/20801) Training Accuracy : 0.953319551944618",
        "2: (19852/20801) Training Accuracy : 0.9543771934041633"));

  }

  @Test
  void testTrainingEndWithStopCriteria() {
    StopCriteria stopCriteria = new IterDeltaAccuracyUnderTolerance(new TrainingParameters(Map.of("Tolerance",
        .00002)));
    progressMonitor.finishedTraining(150, stopCriteria);
    progressMonitor.displayAndClear();

    //Assert that the logs captured the training completion message with StopCriteria satisfied.
    Assertions.assertThat(appender.list.stream().map(ILoggingEvent::getMessage).
            collect(Collectors.toList())).
        isEqualTo(List.of("Stopping: change in training set accuracy less than {2.0E-5}"));
  }

  @Test
  void testTrainingEndWithoutHittingStopCriteria() {
    progressMonitor.finishedTraining(150, null);
    progressMonitor.displayAndClear();

    //Assert that the logs captured the training completion message when all iterations are exhausted.
    Assertions.assertThat(appender.list.stream().map(ILoggingEvent::getMessage).
            collect(Collectors.toList())).
        isEqualTo(List.of("Training Finished after completing 150 Iterations successfully."));
  }

  @Test
  void displayAndClear() {
    progressMonitor.finishedTraining(150, null);
    progressMonitor.displayAndClear();

    //Assert that that previous invocation of displayAndClear has cleared the underlying data structure.
    appender.list.clear();
    progressMonitor.displayAndClear();
    assertEquals(0, appender.list.size());
  }

  @AfterAll
  static void afterAll() {
    logger.setLevel(originalLogLevel);
  }
}
