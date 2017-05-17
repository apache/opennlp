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

package opennlp.tools.formats.leipzig;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;

import opennlp.tools.langdetect.Language;
import opennlp.tools.langdetect.LanguageSample;
import opennlp.tools.util.MarkableFileInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

public class LeipzigLanguageSampleStream implements ObjectStream<LanguageSample> {

  private class LeipzigSentencesStream implements ObjectStream<LanguageSample> {
    private final String lang;
    private int sentencesPerSample;
    private int numberOfSamples;

    private ObjectStream<String> lineStream;
    private int sampleCount;

    LeipzigSentencesStream(String lang, File sentencesFile, int sentencesPerSample, int numberOfSamples)
        throws IOException {
      this.lang = sentencesFile.getName().substring(0, 3);
      this.sentencesPerSample = sentencesPerSample;
      this.numberOfSamples = numberOfSamples;

      lineStream = new PlainTextByLineStream(new MarkableFileInputStreamFactory(sentencesFile),
          StandardCharsets.UTF_8);
    }

    @Override
    public LanguageSample read() throws IOException {

      if (sampleCount < numberOfSamples) {
        StringBuilder sampleString = new StringBuilder();

        int count = 0;
        String line;
        while (count < sentencesPerSample && (line = lineStream.read()) != null) {

          int textStart = line.indexOf('\t') + 1;

          // TODO: It should it be changed to contain an array of sample strings ?!
          sampleString.append(line.substring(textStart) + " ");

          count++;
        }

        if (sampleString.length() > 0) {
          sampleCount++;
          return new LanguageSample(new Language(lang), sampleString);
        }
      }
      return null;
    }
  }

  private final int sentencesPerSample;

  private Map<String, Integer> langSampleCounts;
  private File[] sentencesFiles;

  private Iterator<File> sentencesFilesIt;
  private ObjectStream<LanguageSample> sampleStream;

  public LeipzigLanguageSampleStream(File leipzigFolder, final int sentencesPerSample,
                                     final int samplesPerLanguage) throws IOException {
    this.sentencesPerSample = sentencesPerSample;
    // TODO: Use a FileFilter to make this more reliable in case there are files which should be ignored
    sentencesFiles = leipzigFolder.listFiles();
    Arrays.sort(sentencesFiles);

    Map<String, Integer> langCounts = Arrays.stream(sentencesFiles)
        .map(file -> file.getName().substring(0, 3))
        .collect(Collectors.groupingBy(String::toString, Collectors.summingInt(v -> 1)));

    langSampleCounts = langCounts.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> samplesPerLanguage / e.getValue()));

    reset();
  }

  public LanguageSample read() throws IOException {
    LanguageSample sample;
    if (sampleStream != null && (sample = sampleStream.read()) != null) {
      return sample;
    }
    else {
      if (sentencesFilesIt.hasNext()) {
        File sentencesFile = sentencesFilesIt.next();
        System.out.println(sentencesFile);
        String lang = sentencesFile.getName().substring(0, 3);

        sampleStream = new LeipzigSentencesStream(lang, sentencesFile,
            sentencesPerSample, langSampleCounts.get(lang));

        return read();
      }
    }
    return null;
  }

  @Override
  public void reset() throws IOException {
    sentencesFilesIt = Arrays.asList(sentencesFiles).iterator();
    sampleStream = null;
  }

  public static void main(String[] args) throws Exception {
    new LeipzigLanguageSampleStream(new File("/home/blue/opennlp-data-dir/leipzig-lang"),
        10, 100000);
  }
}
