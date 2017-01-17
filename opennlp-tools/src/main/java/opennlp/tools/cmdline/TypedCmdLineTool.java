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

import java.util.Map;

/**
 * Base class for tools which support processing of samples of some type T
 * coming from a stream of a certain format.
 */
public abstract class TypedCmdLineTool<T>
    extends CmdLineTool {

  /**
   * variable to access the type of the generic parameter.
   */
  protected final Class<T> type;

  /**
   * Constructor with type parameters.
   *
   * @param sampleType class of the template parameter
   */
  protected TypedCmdLineTool(Class<T> sampleType) {
    this.type = sampleType;
  }

  /**
   * Returns stream factory for the type of this tool for the <code>format</code>.
   *
   * @param format data format name
   * @return stream factory for the type of this tool for the format
   */
  protected ObjectStreamFactory<T> getStreamFactory(String format) {
    ObjectStreamFactory<T> factory = StreamFactoryRegistry.getFactory(type, format);
    if (null != factory) {
      return factory;
    } else {
      throw new TerminateToolException(1, "Format " + format + " is not found.\n" + getHelp());
    }
  }

  /**
   * Validates arguments using parameters from <code>argProxyInterface</code> and the parameters of the
   * <code>format</code>.
   *
   * @param args arguments
   * @param argProxyInterface interface with parameter descriptions
   * @param format data format name
   * @param <A> A
   */
  @SuppressWarnings({"unchecked"})
  protected <A> void validateAllArgs(String[] args, Class<A> argProxyInterface, String format) {
    ObjectStreamFactory<T> factory = getStreamFactory(format);
    String errMessage = ArgumentParser.validateArgumentsLoudly(args, argProxyInterface,
        factory.<A>getParameters());
    if (null != errMessage) {
      throw new TerminateToolException(1, errMessage + "\n" + getHelp(format));
    }
  }

  /**
   * Validates arguments for a format processed by the <code>factory</code>.
   * @param factory a stream factory
   * @param args arguments
   */
  protected void validateFactoryArgs(ObjectStreamFactory<T> factory, String[] args) {
    String errMessage = ArgumentParser.validateArgumentsLoudly(args, factory.getParameters());
    if (null != errMessage) {
      throw new TerminateToolException(1, "Format parameters are invalid: " + errMessage + "\n" +
          "Usage: " + ArgumentParser.createUsage(factory.getParameters()));
    }
  }

  @Override
  protected String getBasicHelp(Class<?>... argProxyInterfaces) {
    Map<String, ObjectStreamFactory<T>> factories = StreamFactoryRegistry.getFactories(type);

    String formatsHelp = " ";
    if (1 < factories.size()) {
      StringBuilder formats = new StringBuilder();
      for (String format : factories.keySet()) {
        if (!StreamFactoryRegistry.DEFAULT_FORMAT.equals(format)) {
          formats.append(".").append(format).append("|");
        }
      }
      formatsHelp = "[" + formats.substring(0, formats.length() - 1) + "] ";
    }

    return "Usage: " + CLI.CMD + " " + getName() + formatsHelp +
        ArgumentParser.createUsage(argProxyInterfaces);
  }

  public String getHelp() {
    return getHelp("");
  }

  /**
   * Executes the tool with the given parameters.
   *
   * @param format format to work with
   * @param args command line arguments
   */
  public abstract void run(String format, String args[]);

  /**
   * Retrieves a description on how to use the tool.
   *
   * @param format data format
   * @return a description on how to use the tool
   */
  public abstract String getHelp(String format);
}
