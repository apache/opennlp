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


package opennlp.tools.log;

import java.io.PrintStream;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.event.Level;

import opennlp.tools.commons.Internal;

/**
 * This class serves as an adapter for a {@link Logger} used within a {@link PrintStream}.
 */
@Internal
public class LogPrintStream extends PrintStream {

  private final Logger logger;
  private final Level level;

  /**
   * Creates a {@link LogPrintStream} for the given {@link Logger}.
   *
   * @param logger must not be {@code null}
   */
  public LogPrintStream(Logger logger) {
    this(logger, Level.INFO);
  }

  /**
   * Creates a {@link LogPrintStream} for the given {@link Logger}, which logs at the specified
   * {@link Level level}.
   *
   * @param logger must not be {@code null}
   * @param level  must not be {@code null}
   */
  public LogPrintStream(Logger logger, Level level) {
    super(nullOutputStream());
    Objects.requireNonNull(logger, "logger must not be NULL.");
    Objects.requireNonNull(level, "log level must not be NULL.");
    this.logger = logger;
    this.level = level;
  }

  @Override
  public PrintStream printf(String format, Object... args) {
    log(String.format(format, args));
    return this;
  }

  @Override
  public void println(String msg) {
    log(msg);
  }

  private void log(String msg) {
    switch (level) {
      case TRACE:
        logger.trace(msg);
        break;
      case DEBUG:
        logger.debug(msg);
        break;
      case INFO:
        logger.info(msg);
        break;
      case WARN:
        logger.warn(msg);
        break;
      case ERROR:
        logger.error(msg);
        break;
    }
  }
}
