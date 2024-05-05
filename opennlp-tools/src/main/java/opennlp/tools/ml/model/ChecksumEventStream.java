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

package opennlp.tools.ml.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32C;
import java.util.zip.Checksum;

import opennlp.tools.util.AbstractObjectStream;
import opennlp.tools.util.ObjectStream;

/**
 * A {@link Checksum}-based {@link AbstractObjectStream event stream} implementation.
 * Computes the checksum while consuming the event stream.
 * By default, this implementation will use {@link CRC32C} for checksum calculations
 * as it can use of CPU-specific acceleration instructions at runtime.
 *
 * @see Event
 * @see Checksum
 * @see AbstractObjectStream
 */
public class ChecksumEventStream extends AbstractObjectStream<Event> {

  private final Checksum checksum;


  /**
   * Initializes an {@link ChecksumEventStream}.
   *
   * @param eventStream The {@link ObjectStream} that provides the {@link Event} samples.
   */
  public ChecksumEventStream(ObjectStream<Event> eventStream) {
    super(eventStream);
    // CRC32C supports CPU-specific acceleration instructions
    checksum = new CRC32C();
  }

  @Override
  public Event read() throws IOException {
    Event event = super.read();
    if (event != null) {
      checksum.update(event.toString().getBytes(StandardCharsets.UTF_8));
    }
    return event;
  }

  /**
   * Calculates and returns the (current) checksum.
   * <p>
   * Note: This should be called once the underlying stream has been (fully) consumed.
   *
   * @return The calculated checksum as {@code long}.
   */
  public long calculateChecksum()  {
    return checksum.getValue();
  }
}
