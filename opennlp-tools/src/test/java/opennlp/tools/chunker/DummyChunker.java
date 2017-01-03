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

package opennlp.tools.chunker;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import opennlp.tools.util.Sequence;
import opennlp.tools.util.Span;

/**
 * This dummy chunker implementation reads a file formatted as described at
 * <a hraf="http://www.cnts.ua.ac.be/conll2000/chunking/output.html/">] to
 * simulate a Chunker. The file has samples of sentences, with target and
 * predicted values.
 */
public class DummyChunker implements Chunker {

  private DummyChunkSampleStream mSampleStream;

  public DummyChunker(DummyChunkSampleStream aSampleStream) {
    mSampleStream = aSampleStream;
  }

  public List<String> chunk(List<String> toks, List<String> tags) {
    return Arrays.asList(chunk(toks.toArray(new String[toks.size()]),
        tags.toArray(new String[tags.size()])));
  }

  public String[] chunk(String[] toks, String[] tags) {
    try {
      ChunkSample predsSample = mSampleStream.read();

      // checks if the streams are sync
      for (int i = 0; i < toks.length; i++) {
        if (!toks[i].equals(predsSample.getSentence()[i])
            || !tags[i].equals(predsSample.getTags()[i])) {
          throw new RuntimeException("The streams are not sync!"
              + "\n expected sentence: " + Arrays.toString(toks)
              + "\n expected tags: " + Arrays.toString(tags)
              + "\n predicted sentence: "
              + Arrays.toString(predsSample.getSentence())
              + "\n predicted tags: "
              + Arrays.toString(predsSample.getTags()));
        }
      }

      return predsSample.getPreds();

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public Sequence[] topKSequences(List<String> sentence, List<String> tags) {
    return null;
  }

  public Sequence[] topKSequences(String[] sentence, String[] tags,
      double minSequenceScore) {
    return null;
  }

  public Span[] chunkAsSpans(String[] toks, String[] tags) {
    return null;
  }

  public Sequence[] topKSequences(String[] sentence, String[] tags) {
    return null;
  }

}
