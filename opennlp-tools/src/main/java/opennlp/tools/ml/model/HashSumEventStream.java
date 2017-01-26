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
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import opennlp.tools.util.AbstractObjectStream;
import opennlp.tools.util.ObjectStream;

public class HashSumEventStream extends AbstractObjectStream<Event> {

  private MessageDigest digest;

  public HashSumEventStream(ObjectStream<Event> eventStream) {
    super(eventStream);

    try {
      digest = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      // should never happen, does all java runtimes have md5 ?!
      throw new IllegalStateException(e);
    }
  }

  @Override
  public Event read() throws IOException {
    Event event = super.read();

    if (event != null) {
      digest.update(event.toString().getBytes(StandardCharsets.UTF_8));
    }

    return event;
  }

  /**
   * Calculates the hash sum of the stream. The method must be
   * called after the stream is completely consumed.
   *
   * @return the hash sum
   * @throws IllegalStateException if the stream is not consumed completely,
   *     completely means that hasNext() returns false
   */
  public BigInteger calculateHashSum() {
    return new BigInteger(1, digest.digest());
  }

  public void remove() {
  }
}
