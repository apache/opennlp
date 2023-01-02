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

/**
 * Base class for all command line tools.
 */
public abstract class CmdLineTool {

  protected CmdLineTool() {
  }

  /**
   * @return Retrieves the name of the command line tool. The name (used as command)
   *         must not contain white spaces.
   */
  public String getName() {
    if (getClass().getName().endsWith("Tool")) {
      return getClass().getSimpleName().substring(0, getClass().getSimpleName().length() - 4);
    } else {
      return getClass().getSimpleName();
    }
  }

  /**
   * @return Retrieves whether the tool has any command line params
   */
  public boolean hasParams() {
    return true;
  }

  protected String getBasicHelp(Class<?> argProxyInterface) {
    return getBasicHelp(new Class[]{argProxyInterface});
  }

  protected String getBasicHelp(Class<?>... argProxyInterfaces) {
    return "Usage: " + CLI.CMD + " " + getName() + " " +
        ArgumentParser.createUsage(argProxyInterfaces);
  }

  /**
   * @return Retrieves a description on how to use the tool
   */
  public abstract String getHelp();

  protected <T> T validateAndParseParams(String[] args, Class<T> argProxyInterface) {
    String errorMessage = ArgumentParser.validateArgumentsLoudly(args, argProxyInterface);
    if (null != errorMessage) {
      throw new TerminateToolException(1, errorMessage + "\n" + getHelp());
    }
    return ArgumentParser.parse(args, argProxyInterface);
  }

  /**
   * @return Retrieves a short description of what the tool does
   */
  public String getShortDescription() {
    return "";
  }
}
