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


package opennlp.tools.tokenize;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.ml.model.Event;
import opennlp.tools.tokenize.lang.Factory;
import opennlp.tools.util.AbstractEventStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;

/**
 * This class reads the {@link TokenSample samples} via an {@link Iterator}
 * and converts the samples into {@link Event events} which
 * can be used by the maxent library for training.
 */
public class TokSpanEventStream extends AbstractEventStream<TokenSample> {

  private static final Logger logger = LoggerFactory.getLogger(TokSpanEventStream.class);
  private final TokenContextGenerator cg;

  private final boolean skipAlphaNumerics;

  private final Pattern alphaNumeric;

  /**
   * Initializes a new event stream based on the data stream using a {@link TokenContextGenerator}.
   *
   * @param tokenSamples The {@link ObjectStream data stream} for this event stream.
   * @param skipAlphaNumerics Whether alphanumerics are skipped, or not.
   * @param alphaNumeric A custom alphanumeric {@link Pattern} or {@code null}.
   *                     Default is: {@code "^[A-Za-z0-9]+$"}, provided by
   *                     {@link Factory#DEFAULT_ALPHANUMERIC}.
   * @param cg A {@link TokenContextGenerator} which should be used for the event stream {@code d}.
   */
  public TokSpanEventStream(ObjectStream<TokenSample> tokenSamples, boolean skipAlphaNumerics,
                            Pattern alphaNumeric, TokenContextGenerator cg) {
    super(tokenSamples);
    this.alphaNumeric = alphaNumeric;
    this.skipAlphaNumerics = skipAlphaNumerics;
    this.cg = cg;
  }

  /**
   * Initializes a new event stream based on the data stream using a {@link TokenContextGenerator}.
   *
   * @param tokenSamples The {@link ObjectStream data stream} for this event stream.
   * @param skipAlphaNumerics Whether alphanumerics are skipped, or not.
   * @param cg A {@link TokenContextGenerator} which should be used for the event stream {@code d}.
   */
  public TokSpanEventStream(ObjectStream<TokenSample> tokenSamples, boolean skipAlphaNumerics,
                            TokenContextGenerator cg) {
    this(tokenSamples, skipAlphaNumerics, new Factory().getAlphanumeric(null), cg );
  }

  /**
   * Initializes a new event stream based on the data stream using a {@link TokenContextGenerator}
   * that relies on a {@link DefaultTokenContextGenerator}.
   *
   * @param tokenSamples The {@link ObjectStream data stream} for this event stream.
   * @param skipAlphaNumerics Whether alphanumerics are skipped, or not.
   */
  public TokSpanEventStream(ObjectStream<TokenSample> tokenSamples,
      boolean skipAlphaNumerics) {
    this(tokenSamples, skipAlphaNumerics, new DefaultTokenContextGenerator());
  }

  /**
   * Adds training events to the event stream for each of the specified {@link TokenSample sample}.
   *
   * @param tokenSample character offsets into the specified text.
   * @return An {@link Iterator} for text {@link Event events} representing the {@code tokenSample}.
   */
  @Override
  protected Iterator<Event> createEvents(TokenSample tokenSample) {

    List<Event> events = new ArrayList<>(50);

    Span[] tokens = tokenSample.getTokenSpans();
    String text = tokenSample.getText();

    if (tokens.length > 0) {

      int start = tokens[0].getStart();
      int end = tokens[tokens.length - 1].getEnd();

      String sent = text.substring(start, end);

      Span[] candTokens = WhitespaceTokenizer.INSTANCE.tokenizePos(sent);

      int firstTrainingToken = -1;
      int lastTrainingToken = -1;
      for (Span candToken : candTokens) {
        Span cSpan = candToken;
        String ctok = sent.substring(cSpan.getStart(), cSpan.getEnd());
        //adjust cSpan to text offsets
        cSpan = new Span(cSpan.getStart() + start, cSpan.getEnd() + start);
        //should we skip this token
        if (ctok.length() > 1 && (!skipAlphaNumerics || !alphaNumeric.matcher(ctok).matches())) {

          //find offsets of annotated tokens inside of candidate tokens
          boolean foundTrainingTokens = false;
          for (int ti = lastTrainingToken + 1; ti < tokens.length; ti++) {
            if (cSpan.contains(tokens[ti])) {
              if (!foundTrainingTokens) {
                firstTrainingToken = ti;
                foundTrainingTokens = true;
              }
              lastTrainingToken = ti;
            }
            else if (cSpan.getEnd() < tokens[ti].getEnd()) {
              break;
            }
            else if (tokens[ti].getEnd() < cSpan.getStart()) {
              //keep looking
            }
            else {
              logger.warn("Bad training token: {} cand: {} token={}", tokens[ti], cSpan,
                  text.substring(tokens[ti].getStart(), tokens[ti].getEnd()));
            }
          }

          // create training data
          if (foundTrainingTokens) {

            for (int ti = firstTrainingToken; ti <= lastTrainingToken; ti++) {
              Span tSpan = tokens[ti];
              int cStart = cSpan.getStart();
              for (int i = tSpan.getStart() + 1; i < tSpan.getEnd(); i++) {
                String[] context = cg.getContext(ctok, i - cStart);
                events.add(new Event(TokenizerME.NO_SPLIT, context));
              }

              if (tSpan.getEnd() != cSpan.getEnd()) {
                String[] context = cg.getContext(ctok, tSpan.getEnd() - cStart);
                events.add(new Event(TokenizerME.SPLIT, context));
              }
            }
          }
        }
      }
    }

    return events.iterator();
  }
}
