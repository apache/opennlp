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

package opennlp.tools.ml.naivebayes;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.zip.GZIPOutputStream;

import opennlp.tools.ml.model.AbstractModel;

/**
 * A {@link NaiveBayesModelWriter} that writes models in a plain text format.
 */
public class PlainTextNaiveBayesModelWriter extends NaiveBayesModelWriter {
  private final BufferedWriter output;

  /**
   * Instantiates {@link PlainTextNaiveBayesModelWriter} via an
   * {@link AbstractModel naive bayes model} and a {@link File}.
   * Prepares writing of a {@code model} to the file.
   * Based on whether the file's suffix contains {@code .gz}, it detects whether
   * the file is gzipped or not.
   *
   * @param model The {@link AbstractModel naive bayes model} which is to be persisted.
   * @param f The {@link File} in which the model is to be persisted.
   *
   * @throws IOException Thrown if IO errors occurred.
   * @see NaiveBayesModel
   */
  public PlainTextNaiveBayesModelWriter(AbstractModel model, File f) throws IOException {

    super(model);
    if (f.getName().endsWith(".gz")) {
      output = new BufferedWriter(new OutputStreamWriter(
          new GZIPOutputStream(new FileOutputStream(f))));
    } else {
      output = new BufferedWriter(new FileWriter(f));
    }
  }

  /**
   * Instantiates {@link PlainTextNaiveBayesModelWriter} via
   * an {@link AbstractModel naive bayes model} and a {@link BufferedWriter}.
   * Prepares writing a {@code model} to the file.
   * Based on whether the file's suffix contains {@code .gz}, it detects whether
   * the file is gzipped or not.
   *
   * @param model The {@link AbstractModel naive bayes model} which is to be persisted.
   * @param bw The {@link BufferedWriter} which is used to persist the {@code model}.
   *            The {@code bw} must be opened.
   */
  public PlainTextNaiveBayesModelWriter(AbstractModel model, BufferedWriter bw) {
    super(model);
    output = bw;
  }

  @Override
  public void writeUTF(String s) throws IOException {
    output.write(s);
    output.newLine();
  }

  @Override
  public void writeInt(int i) throws IOException {
    output.write(Integer.toString(i));
    output.newLine();
  }

  @Override
  public void writeDouble(double d) throws IOException {
    output.write(Double.toString(d));
    output.newLine();
  }

  @Override
  public void close() throws IOException {
    output.flush();
    output.close();
  }

}
