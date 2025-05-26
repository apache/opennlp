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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.langdetect.Language;
import opennlp.tools.langdetect.LanguageSample;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.MarkableFileInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

public class LeipzigLanguageSampleStream implements ObjectStream<LanguageSample> {

  private class LeipzigSentencesStream implements ObjectStream<LanguageSample> {

    private final String lang;

    private final Iterator<String> lineIterator;

    /**
     * Initializes a {@link LeipzigSentencesStream}.
     *
     * @param lang An ISO language code.
     * @param sentencesFile The {@link File} which contains sentences to process.
     * @param sentencesPerSample The number of sentences per sample.
     * @param numberOfSamples The number of samples to process at maximum.
     *                        
     * @throws IOException Thrown if IO errors occurred.
     * @throws InvalidFormatException Thrown if {@code sentencesFile} has not enough lines to process.
     */
    LeipzigSentencesStream(String lang, File sentencesFile, int sentencesPerSample, int numberOfSamples)
        throws IOException {

      this.lang = lang;

      // The file name contains the number of lines, but to make this more stable
      // the file is once scanned for the count even tough this is slower
      int totalLineCount = (int) Files.lines(sentencesFile.toPath()).count();
      int requiredLines = sentencesPerSample * numberOfSamples;

      if (totalLineCount < requiredLines)
        throw new InvalidFormatException(
                String.format("%s does not contain enough lines (%d lines < %d required lines).",
                        sentencesFile.getPath(), totalLineCount, requiredLines));

      List<Integer> indexes = IntStream.range(0, totalLineCount)
          .boxed().collect(Collectors.toList());

      Collections.shuffle(indexes, random);

      Set<Integer> selectedLines = new HashSet<>(indexes.subList(0, requiredLines));

      List<String> sentences = new ArrayList<>();

      try (ObjectStream<String> lineStream = new PlainTextByLineStream(
          new MarkableFileInputStreamFactory(sentencesFile), StandardCharsets.UTF_8)) {

        int lineIndex = 0;
        String line;
        while ((line = lineStream.read()) != null) {

          int tabIndex = line.indexOf('\t');
          if (tabIndex != -1) {
            if (selectedLines.contains(lineIndex)) {
              sentences.add(line);
            }
          }

          lineIndex++;
        }
      }

      Collections.shuffle(sentences, random);

      lineIterator = sentences.iterator();
    }

    @Override
    public LanguageSample read() throws IOException {
      StringBuilder sampleString = new StringBuilder();

      int count = 0;
      while (count < sentencesPerSample && lineIterator.hasNext()) {

        String line = lineIterator.next();
        int textStart = line.indexOf('\t') + 1;

        sampleString.append(line.substring(textStart)).append(" ");

        count++;
      }

      if (sampleString.length() > 0) {
        return new LanguageSample(new Language(lang), sampleString);
      }

      return null;
    }
  }

  private final int sentencesPerSample;

  private final Map<String, Integer> langSampleCounts;
  private final File[] sentencesFiles;

  private Iterator<File> sentencesFilesIt;
  private ObjectStream<LanguageSample> sampleStream;

  private final Random random;

  /**
   * Initializes a {@link LeipzigLanguageSampleStream}.
   *
   * @param leipzigFolder The {@link File directory} which contains files to process.
   * @param sentencesPerSample The number of sentences per sample.
   * @param samplesPerLanguage The number of samples per language to process at maximum.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  public LeipzigLanguageSampleStream(File leipzigFolder, final int sentencesPerSample,
                                     final int samplesPerLanguage) throws IOException {
    this.sentencesPerSample = sentencesPerSample;

    sentencesFiles = leipzigFolder.listFiles(pathname -> !pathname.isHidden() && pathname.isFile()
            && pathname.getName().length() >= 3
            && pathname.getName().substring(0,3).matches("[a-z]+"));

    if (null == sentencesFiles) {
      throw new TerminateToolException(-1 , "Directory " + leipzigFolder + " empty , No files to read!");
    }

    Arrays.sort(sentencesFiles);

    Map<String, Integer> langCounts = Arrays.stream(sentencesFiles)
        .map(file -> file.getName().substring(0, 3))
        .collect(Collectors.groupingBy(String::toString, Collectors.summingInt(v -> 1)));

    langSampleCounts = langCounts.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> samplesPerLanguage / e.getValue()));

    random = new Random(23);

    reset();
  }

  @Override
  public LanguageSample read() throws IOException {
    LanguageSample sample;
    if (sampleStream != null && (sample = sampleStream.read()) != null) {
      return sample;
    }
    else {
      if (sentencesFilesIt.hasNext()) {
        File sentencesFile = sentencesFilesIt.next();

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
}
