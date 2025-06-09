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

import com.ginsberg.junit.exit.ExpectSystemExitWithStatus;
import org.junit.jupiter.api.Test;

public class CLITest {

  /**
   * Ensure the main method does not fail to print help message.
   */
  @Test
  @ExpectSystemExitWithStatus(0)
  void testMainHelpMessage() {
    CLI.main(new String[] {});
  }

  /**
   * Ensure the main method prints error and returns 1.
   */
  @Test
  @ExpectSystemExitWithStatus(1)
  void testUnknownToolMessage() {
    CLI.main(new String[] {"unknown name"});
  }

  /**
   * Ensure the tool checks the parameter and returns 1.
   */
  @Test
  @ExpectSystemExitWithStatus(1)
  void testToolParameterMessage() {
    CLI.main(new String[] {"DoccatTrainer", "-param", "value"});
  }

  /**
   * Ensure the main method prints error and returns -1
   */
  @Test
  @ExpectSystemExitWithStatus(-1)
  void testUnknownFileMessage() {
    CLI.main(new String[] {"Doccat", "unknown.model"});
  }


  /**
   * Ensure all tools do not fail printing help message;
   */
  @Test
  @ExpectSystemExitWithStatus(0)
  void testHelpMessageOfTools() {
    for (String toolName : CLI.getToolNames()) {
      CLI.main(new String[] {toolName, "help"});
    }
  }

}
