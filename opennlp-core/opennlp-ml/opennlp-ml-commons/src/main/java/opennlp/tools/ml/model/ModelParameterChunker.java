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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UTFDataFormatException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A helper class that handles Strings with more than 64k (65535 bytes) in length.
 * This is achieved via the signature {@link #SIGNATURE_CHUNKED_PARAMS} at the beginning of
 * the String instance to be written to a {@link DataOutputStream}.
 * <p>
 * Background: In OpenNLP, for large(r) corpora, we train models whose (UTF String) parameters will exceed
 * the {@link #MAX_CHUNK_SIZE_BYTES} bytes limit set in {@link DataOutputStream}.
 * For writing and reading those models, we have to chunk up those string instances in 64kB blocks and
 * recombine them correctly upon reading a (binary) model file.
 * <p>
 * The problem was raised in <a href="https://issues.apache.org/jira/browse/OPENNLP-1366">ticket OPENNLP-1366</a>.
 * <p>
 * Solution strategy:
 * <ul>
 * <li>If writing parameters to a {@link DataOutputStream} blows up with a {@link UTFDataFormatException} a
 * large String instance is chunked up and written as appropriate blocks.</li>
 * <li>To indicate that chunking was conducted, we start with the {@link #SIGNATURE_CHUNKED_PARAMS} indicator,
 * directly followed by the number of chunks used. This way, when reading in chunked model parameters,
 * recombination is achieved transparently.</li>
 * </ul>
 * <p>
 * Note: Both, existing (binary) model files and newly trained models which don't require the chunking
 * technique, will be supported like in previous OpenNLP versions.
 *
 * @author <a href="mailto:martin.wiesner@hs-heilbronn.de">Martin Wiesner</a>
 * @author <a href="mailto:struberg@apache.org">Mark Struberg</a>
 */
public final class ModelParameterChunker {

  /*
   * A signature that denotes the start of a String that required chunking.
   *
   * Semantics:
   * If a model parameter (String) carries the below signature at the very beginning, this indicates
   * that 'n > 1' chunks must be processed to obtain the whole model parameters. Otherwise, those would not be
   * written to the binary model files (as reported in OPENNLP-1366) if the training occurs on large corpora
   * as used, for instance, in the context of (very large) German NLP models.
   */
  public static final String SIGNATURE_CHUNKED_PARAMS = "CHUNKED-MODEL-PARAMS:"; // followed by no of chunks!

  private static final int MAX_CHUNK_SIZE_BYTES = 65535; // the maximum 'utflen' DataOutputStream can handle

  private ModelParameterChunker() {
    // private utility class ct s
  }

  /**
   * Reads model parameters from {@code dis}. In case the stream start with {@link #SIGNATURE_CHUNKED_PARAMS},
   * the number of chunks is detected and the original large parameter string is reconstructed from several
   * chunks.
   *
   * @param dis   The stream which will be used to read the model parameter from.
   */
  public static String readUTF(DataInputStream dis) throws IOException {
    String data = dis.readUTF();
    if (data.startsWith(SIGNATURE_CHUNKED_PARAMS)) {
      String chunkElements = data.replace(SIGNATURE_CHUNKED_PARAMS, "");
      int chunkSize = Integer.parseInt(chunkElements);
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < chunkSize; i++) {
        sb.append(dis.readUTF());
      }
      return sb.toString(); // the reconstructed model parameter string
    } else {  // default case: no chunked data -> just return the read data / parameter information
      return data;
    }
  }

  /**
   * Writes the model parameter {@code s} to {@code dos}. In case {@code s} does exceed
   * {@link #MAX_CHUNK_SIZE_BYTES} in length, the chunking mechanism is used; otherwise the parameter is
   * written 'as is'.
   *
   * @param dos   The {@link DataOutputStream} stream which will be used to persist the model.
   * @param s     The input string that is checked for length and chunked if {@link #MAX_CHUNK_SIZE_BYTES} is
   *              exceeded.
   */
  public static void writeUTF(DataOutputStream dos, String s) throws IOException {
    try {
      dos.writeUTF(s);
    } catch (UTFDataFormatException dfe) {
      // we definitely have to chunk the given model parameter 's' as it exceeds the bytes allowed for 1 chunk
      final String[] chunks = splitByByteLength(s);
      // write the signature string with the amount of chunks for reading the model file correctly
      dos.writeUTF(SIGNATURE_CHUNKED_PARAMS + chunks.length); // add number of required chunks
      for (String c: chunks) {
        dos.writeUTF(c);
      }
    }
  }

  private static String[] splitByByteLength(String input) {
    CharBuffer in = CharBuffer.wrap(input);
    ByteBuffer out = ByteBuffer.allocate(MAX_CHUNK_SIZE_BYTES);  // output buffer of required size
    CharsetEncoder coder = StandardCharsets.UTF_8.newEncoder();
    List<String> chunks = new ArrayList<>();
    int pos = 0;
    while (true) {
      CoderResult cr = coder.encode(in, out, true);
      int nPos = input.length() - in.length();
      String s = input.substring(pos, nPos);
      chunks.add(s);
      pos = nPos;
      out.rewind();
      if (! cr.isOverflow()) {
        break;
      }
    }
    return chunks.toArray(new String[0]);
  }
}
