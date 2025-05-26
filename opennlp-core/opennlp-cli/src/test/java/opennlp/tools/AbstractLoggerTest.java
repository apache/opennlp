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
import org.slf4j.LoggerFactory;

/**
 * An abstract class to configure the {@link Logger} instance to help with unit-testing.
 */
public abstract class AbstractLoggerTest {

  public static final String LOGGER_OPENNLP = "opennlp";

  /**
   * Prepare the logging resource.
   * @param loggerName Name of the {@link Logger}.
   */
  public static void prepare(String loggerName) {
    getLogger(loggerName).setLevel(Level.INFO);
  }

  /*
   * Restores the logging resource to its default config.
   */
  public static void restore(String loggerName) {
    getLogger(loggerName).setLevel(Level.OFF);
  }

  private static Logger getLogger(String loggerName) {
    Logger logger = (Logger) LoggerFactory.getLogger(loggerName);
    if (Objects.isNull(logger)) {
      throw new IllegalArgumentException("A logger instance couldn't be created for the given logger "
          + loggerName);
    }
    return logger;
  }
}

