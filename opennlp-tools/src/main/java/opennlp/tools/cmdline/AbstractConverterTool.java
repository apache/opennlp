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

import java.io.IOException;
import java.util.Map;

import opennlp.tools.util.ObjectStream;

/**
 * Base class for format conversion tools.
 *
 * @param <T> class of data sample the tool converts, for example {@link opennlp.tools.postag
 * .POSSample}
 */
public abstract class AbstractConverterTool<T> extends TypedCmdLineTool<T> {

  /**
   * Constructor with type parameter.
   *
   * @param sampleType class of the template parameter
   */
  protected AbstractConverterTool(Class<T> sampleType) {
    super(sampleType);
  }

  public String getShortDescription() {
    Map<String, ObjectStreamFactory<T>> factories = StreamFactoryRegistry.getFactories(type);
    StringBuilder help = new StringBuilder();
    if (2 == factories.keySet().size()) { //opennlp + foreign
      for (String format : factories.keySet()) {
        if (!StreamFactoryRegistry.DEFAULT_FORMAT.equals(format)) {
          help.append(format);
        }
      }
      return "converts " + help.toString() + " data format to native OpenNLP format";
    } else if (2 < factories.keySet().size()) {
      for (String format : factories.keySet()) {
        if (!StreamFactoryRegistry.DEFAULT_FORMAT.equals(format)) {
          help.append(format).append(",");
        }
      }
      return "converts foreign data formats (" + help.substring(0, help.length() - 1 ) +
          ") to native OpenNLP format";
    } else {
      throw new AssertionError("There should be more than 1 factory registered for converter " +
          "tool");
    }
  }

  private String createHelpString(String format, String usage) {
    return "Usage: " + CLI.CMD + " " + getName() + " " + format + " " + usage;
  }

  public String getHelp() {
    Map<String, ObjectStreamFactory<T>> factories = StreamFactoryRegistry.getFactories(type);
    StringBuilder help = new StringBuilder("help|");
    for (String formatName : factories.keySet()) {
      if (!StreamFactoryRegistry.DEFAULT_FORMAT.equals(formatName)) {
        help.append(formatName).append("|");
      }
    }
    return createHelpString(help.substring(0, help.length() - 1), "[help|options...]");
  }

  public String getHelp(String format) {
    return getHelp();
  }

  public void run(String format, String[] args) {
    if (0 == args.length) {
      System.out.println(getHelp());
    } else {
      format = args[0];
      ObjectStreamFactory<T> streamFactory = getStreamFactory(format);

      String formatArgs[] = new String[args.length - 1];
      System.arraycopy(args, 1, formatArgs, 0, formatArgs.length);

      String helpString = createHelpString(format, ArgumentParser.createUsage(streamFactory.getParameters()));
      if (0 == formatArgs.length || (1 == formatArgs.length && "help".equals(formatArgs[0]))) {
        System.out.println(helpString);
        System.exit(0);
      }

      String errorMessage = ArgumentParser.validateArgumentsLoudly(formatArgs, streamFactory.getParameters());
      if (null != errorMessage) {
        throw new TerminateToolException(1, errorMessage + "\n" + helpString);
      }

      try (ObjectStream<T> sampleStream = streamFactory.create(formatArgs)) {
        Object sample;
        while ((sample = sampleStream.read()) != null) {
          System.out.println(sample.toString());
        }
      }
      catch (IOException e) {
        throw new TerminateToolException(-1, "IO error while converting data : " + e.getMessage(), e);
      }
    }
  }
}
