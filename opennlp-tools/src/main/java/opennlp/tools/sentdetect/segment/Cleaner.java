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

import java.util.ArrayList;
import java.util.List;

/**
 * removes errant newlines, xhtml, inline formatting, etc.
 */
public class Cleaner {

  public List<Clean> cleanList = new ArrayList<Clean>();

  public String clean(String text) {
    for (Clean clean : cleanList) {
      text = text.replaceAll(clean.getRegex(), clean.getReplacement());
    }
    return text;
  }

  public void clear() {
    if (cleanList != null) {
      cleanList.clear();
    }
  }

  /**
   * TODO: Move rules into profiles
   */
  public void rules() {

    cleanList.add(new Clean("\\n(?=[a-zA-Z]{1,2}\\n)", ""));

    cleanList.add(new Clean("\\n \\n", "\n"));

    cleanList.add(new Clean("\\n\\n", "\n"));

    cleanList.add(new Clean("\\n(?=\\.(\\s|\\n))", ""));
    cleanList.add(new Clean("(?<=\\s)\\n", ""));
    cleanList.add(new Clean("(?<=\\S)\\n(?=\\S)", " \n "));
    cleanList.add(new Clean("\\n", "\n"));
    cleanList.add(new Clean("\\\\n", "\n"));
    cleanList.add(new Clean("\\\\\\ n", "\n"));

    cleanList.add(new Clean("\\{b\\^&gt;\\d*&lt;b\\^\\}|\\{b\\^>\\d*<b\\^\\}",""));

    cleanList.add(new Clean("\\.{4,}\\s*\\d+-*\\d*","\r"));

//    cleanList.add(new Clean("\\.{5,}", " "));
    cleanList.add(new Clean("\\/{3}", ""));

//    cleanList.add(new Clean("(?<=[a-z])\\.(?=[A-Z])", ". "));
//    cleanList.add(new Clean("(?<=\\d)\\.(?=[A-Z])", ". "));

    cleanList.add(new Clean("\\n(?=•')", "\r"));
    cleanList.add(new Clean("''", "\""));
    cleanList.add(new Clean("``", "\""));

  }

  public void html() {
    cleanList.add(new Clean("<\\/?\\w+((\\s+\\w+(\\s*=\\s*(?:\\\".*?\\\"|'.*?'|" +
        "[\\^'\\\">\\s]+))?)+\\s*|\\s*)\\/?>", ""));
    cleanList.add(new Clean("&lt;\\/?[^gt;]*gt;", ""));
  }

  public void pdf() {
    cleanList.add(new Clean("(?<=[^\\n]\\s)\\n(?=\\S)", ""));
    cleanList.add(new Clean("\\n(?=[a-z])", " "));
  }
}
