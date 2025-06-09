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

package opennlp.tools.ml;

public enum AlgorithmType {

  MAXENT("MAXENT", "GIS",
      "opennlp.tools.ml.maxent.GISTrainer",
      "opennlp.tools.ml.maxent.io.GISModelReader",
      "opennlp.tools.ml.maxent.io.BinaryGISModelWriter"),
  MAXENT_QN("MAXENT_QN", "QN",
      "opennlp.tools.ml.maxent.quasinewton.QNTrainer",
      "opennlp.tools.ml.maxent.io.QNModelReader",
      "opennlp.tools.ml.maxent.io.BinaryQNModelWriter"),
  PERCEPTRON("PERCEPTRON", "Perceptron",
      "opennlp.tools.ml.perceptron.PerceptronTrainer",
      "opennlp.tools.ml.perceptron.PerceptronModelReader",
      "opennlp.tools.ml.perceptron.BinaryPerceptronModelWriter"),
  PERCEPTRON_SEQUENCE("PERCEPTRON_SEQUENCE", "Perceptron",
      "opennlp.tools.ml.perceptron.SimplePerceptronSequenceTrainer",
      "opennlp.tools.ml.perceptron.PerceptronModelReader",
      "opennlp.tools.ml.perceptron.BinaryPerceptronModelWriter"),
  NAIVE_BAYES("NAIVEBAYES", "NaiveBayes",
      "opennlp.tools.ml.naivebayes.NaiveBayesTrainer",
      "opennlp.tools.ml.naivebayes.NaiveBayesModelReader",
      "opennlp.tools.ml.naivebayes.BinaryNaiveBayesModelWriter");


  private final String algorithmType;
  private final String trainerClazz;
  private final String modelType;
  private final String readerClazz;
  private final String writerClazz;

  AlgorithmType(String type, String ioType,
                String trainerClazz, String readerClazz, String writerClazz) {
    this.algorithmType = type;
    this.trainerClazz = trainerClazz;
    this.modelType = ioType;
    this.readerClazz = readerClazz;
    this.writerClazz = writerClazz;
  }

  public String getAlgorithmType() {
    return algorithmType;
  }

  public String getTrainerClazz() {
    return trainerClazz;
  }

  public String getModelType() {
    return modelType;
  }

  public String getReaderClazz() {
    return readerClazz;
  }

  public String getWriterClazz() {
    return writerClazz;
  }

  /**
   * @param type no restriction on the type.
   * @return the {@link AlgorithmType} corresponding to the given algorithm type.
   * @throws IllegalArgumentException if the given type is not a valid {@link AlgorithmType}.
   */
  public static AlgorithmType fromAlgorithmType(String type) {
    for (AlgorithmType trainerType : AlgorithmType.values()) {
      if (trainerType.algorithmType.equals(type)) {
        return trainerType;
      }
    }
    throw new IllegalArgumentException("Unknown algorithm type: " + type);
  }

  /**
   * @param type no restriction on the type.
   * @return the {@link AlgorithmType} corresponding to the given reader type.
   * @throws IllegalArgumentException if the given type is not a valid {@link AlgorithmType}.
   */
  public static AlgorithmType fromModelType(String type) {
    for (AlgorithmType trainerType : AlgorithmType.values()) {
      if (trainerType.modelType.equals(type)) {
        return trainerType;
      }
    }
    throw new IllegalArgumentException("Unknown reader type: " + type);
  }


}
