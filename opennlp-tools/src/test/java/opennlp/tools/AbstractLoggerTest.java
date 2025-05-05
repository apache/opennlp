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

package opennlp.tools;

import java.util.Objects;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterAll;
import org.slf4j.LoggerFactory;

/**
 * An abstract class to configure a {@link Logger} instance with a test {@link ListAppender}
 * to help with unit-testing.
 */
public abstract class AbstractLoggerTest {

  private Logger logger;
  private Level originalLogLevel;
  protected ListAppender<ILoggingEvent> appender;

  public void setUp(String loggerName, Level logLevel) {
    logger = (Logger) LoggerFactory.getLogger(loggerName);
    if (Objects.isNull(logger)) {
      throw new IllegalArgumentException("A logger instance couldn't be created for the given logger "
          + loggerName);
    }
    originalLogLevel = logger.getLevel();
    appender = new ListAppender<>();
    logger.setLevel(logLevel);
    logger.addAppender(appender);
    appender.start();
  }

  /**
   * Restores {@link Logger} configuration after all tests are complete.
   */
  @AfterAll
  protected void afterAll() {
    logger.setLevel(originalLogLevel);
    appender.stop();
  }
}
