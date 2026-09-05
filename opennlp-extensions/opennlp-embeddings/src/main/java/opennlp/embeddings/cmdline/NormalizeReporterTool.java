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

package opennlp.embeddings.cmdline;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import opennlp.embeddings.corpus.CapVolumeReader;
import opennlp.embeddings.corpus.CasePassage;
import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.util.InvalidFormatException;

/**
 * Normalizes raw Caselaw Access Project volume zips into the passages JSON Lines
 * interchange file. See {@link CapVolumeReader} for the passage rules.
 */
public class NormalizeReporterTool extends BasicCmdLineTool {

  interface Params extends NormalizeReporterParams {
  }

  /** {@inheritDoc} */
  @Override
  public String getShortDescription() {
    return "Normalizes Caselaw Access Project volume zips into an opinion-passage JSONL file";
  }

  /** {@inheritDoc} */
  @Override
  public String getHelp() {
    return getBasicHelp(Params.class);
  }

  /** {@inheritDoc} */
  @Override
  public void run(String[] args) {
    final Params params = validateAndParseParams(args, Params.class);
    final List<CasePassage> passages = new ArrayList<>();
    try {
      final File[] zips = params.getRawDir()
          .listFiles((dir, name) -> name.endsWith(".zip"));
      if (zips == null || zips.length == 0) {
        throw new TerminateToolException(1, "No .zip files in " + params.getRawDir());
      }
      Arrays.sort(zips);
      for (File zip : zips) {
        passages.addAll(CapVolumeReader.read(zip.toPath()));
      }
      CasePassage.writeJsonl(passages, params.getOut().toPath());
    } catch (IllegalArgumentException | InvalidFormatException e) {
      throw new TerminateToolException(1, e.getMessage(), e);
    } catch (IOException e) {
      throw new TerminateToolException(-1,
          "IO error while normalizing " + params.getRawDir() + ": " + e.getMessage(), e);
    }
    System.out.println("Wrote " + passages.size() + " passages to " + params.getOut());
  }
}
