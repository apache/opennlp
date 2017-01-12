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

package opennlp.tools.cmdline;

import opennlp.tools.util.ObjectStream;

/**
 * Base class for evaluator tools.
 */
public class AbstractEvaluatorTool<T, P> extends AbstractTypedParamTool<T, P> {

  protected P params;
  protected ObjectStreamFactory<T> factory;
  protected ObjectStream<T> sampleStream;

  /**
   * Constructor with type parameters.
   *
   * @param sampleType class of the template parameter
   * @param params     interface with parameters
   */
  protected AbstractEvaluatorTool(Class<T> sampleType, Class<P> params) {
    super(sampleType, params);
  }

  public void run(String format, String[] args) {
    validateAllArgs(args, this.paramsClass, format);

    params = ArgumentParser.parse(
        ArgumentParser.filter(args, this.paramsClass), this.paramsClass);

    factory = getStreamFactory(format);
    String[] fargs = ArgumentParser.filter(args, factory.getParameters());
    validateFactoryArgs(factory, fargs);
    sampleStream = factory.create(fargs);
  }
}