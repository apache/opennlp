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

package opennlp.tools.sentdetect.segment;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import opennlp.tools.util.StringUtil;


public class SentenceTokenizerME implements SentenceTokenizer {

  private String sentence;

  private int start;

  private int end;

  private CharSequence text;

  private Reader reader;

  private int bufferLength;

  private LanguageTool languageTool;

  private Matcher beforeMatcher;

  private Matcher afterMatcher;

  boolean found;

  private Set<Integer> breakSections;

  private List<Section> noBreakSections;

  public SentenceTokenizerME(LanguageTool languageTool, CharSequence text) {
    this.text = text;
    this.reader = null;
    this.bufferLength = text.length();
    this.languageTool = languageTool;
    this.sentence = null;
    this.start = 0;
    this.end = 0;
  }

  public SentenceTokenizerME(LanguageTool languageTool, Reader reader, int bufferLength) {
    if (bufferLength <= 0) {
      throw new IllegalArgumentException("Buffer size: " + bufferLength +
          " must be positive.");
    }
    this.text = null;
    this.reader = reader;
    this.bufferLength = bufferLength;
    this.languageTool = languageTool;
    this.sentence = null;
    this.start = 0;
    this.end = 0;
  }

  public List<String> sentenceTokenizer() {

    List<String> sentenceList = new ArrayList<>();
    CharSequence text = getText();
    if (breakSections == null) {
      getBreakSections();
    }
    for (Integer breakSection : breakSections) {
      if (breakSection == 0) {
        continue;
      }
      if (breakSection >= text.length()) {
        break;
      }
      end = breakSection;
      if (!isBreak()) {
        continue;
      }
      sentence = text.subSequence(start, end).toString();
      start = end;

      sentence = removeSpace(sentence);
      if (sentence != null) {
        sentenceList.add(sentence);
      }
    }
    if (end < text.length()) {
      end = text.length();
      sentence = text.subSequence(start, end).toString();
      sentence = removeSpace(sentence);
      if (sentence != null) {
        sentenceList.add(sentence);
      }
    }
    return sentenceList;
  }

  public String removeSpace(String segment) {
    if (segment != null) {
      int first = 0;
      int last = segment.length();
      while (first < segment.length() && StringUtil.isWhitespace(segment.charAt(first))) {
        first++;
      }
      while (last > 0 && StringUtil.isWhitespace(segment.charAt(last - 1))) {
        last--;
      }
      if (last - first > 0) {
        return segment.substring(first, last);
      }
    }
    return null;
  }

  public Set<Integer> getBreakSections() {
    if (breakSections == null) {
      breakSections = new TreeSet<Integer>();
      for (Rule rule : languageTool.getBreakRuleList()) {

        Pattern beforePattern = languageTool.compile(rule.getBeforePattern());
        Pattern afterPattern = languageTool.compile(rule.getAfterPattern());
        this.beforeMatcher = beforePattern.matcher(text);
        this.afterMatcher = afterPattern.matcher(text);
        this.found = true;
        while (find()) {
          breakSections.add(getBreakPosition());
        }
      }
    }
    return breakSections;
  }

  private boolean find() {
    found = false;
    while ((!found) && beforeMatcher.find()) {
      afterMatcher.region(beforeMatcher.end(), text.length());
      found = afterMatcher.lookingAt();
    }
    return found;
  }

  private int getBreakPosition() {
    return afterMatcher.start();
  }

  public List<Section> getNoBreakSections() {
    if (noBreakSections == null) {
      noBreakSections = new ArrayList<Section>();
      Pattern pattern = languageTool.getNoBreakPattern();
      Matcher matcher = pattern.matcher(getText());
      while (matcher.find()) {
        noBreakSections.add(new Section(matcher.start(), matcher.end()));
      }
    }
    return noBreakSections;
  }

  public CharSequence getText() {
    if (text == null) {
      text = read(bufferLength + 1);
    }
    return text;
  }

  private String read(int amount) {
    char[] charBuffer = new char[amount];
    int count = read(reader, charBuffer);

    String result;
    if (count == amount) {
      result = new String(charBuffer, 0, count - 1);
    } else if (count > 0 && count < amount) {
      result = new String(charBuffer, 0, count);
    } else {
      result = "";
    }

    return result;
  }

  private int read(Reader reader, char[] buffer) {

    int start = 0;
    int count;

    try {
      while (true) {
        if (!(((count = reader.read(buffer, start, buffer.length - start)) != -1)
            && start < buffer.length)) {
          break;
        }
        start += count;
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return start;
  }

  private boolean isBreak() {
    if (noBreakSections == null) {
      getNoBreakSections();
    }
    if (noBreakSections != null && noBreakSections.size() > 0) {
      for (Section section : noBreakSections) {
        if (end >= section.getLeft() && end <= section.getRight()) {
          return false;
        }
      }
    }
    return true;
  }

}
