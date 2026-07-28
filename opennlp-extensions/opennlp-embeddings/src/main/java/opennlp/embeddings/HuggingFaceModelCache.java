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
package opennlp.embeddings;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * Fetches the files a distillation needs from a Hugging Face model repository into a local cache
 * directory, so a teacher can be named by its hub id ({@code org/model}) instead of a local path.
 * Files download once and are reused afterwards; a file the repository does not have (a 404, e.g.
 * a WordPiece teacher's {@code sentencepiece.bpe.model}) is reported as absent, not an error.
 */
final class HuggingFaceModelCache {

  /** The hub's file-download endpoint pattern: {@code BASE}/{id}/resolve/main/{file}. */
  private static final String RESOLVE_BASE = "https://huggingface.co/";

  /** The ONNX graph of a hub transformer, relative to the repository root. */
  private static final String ONNX_MODEL = "onnx/model.onnx";

  /** The files a distillation needs, relative to the repository root. */
  private static final String[] REQUIRED_FILES = {"tokenizer.json", ONNX_MODEL};

  /** The files used when present: the pad-token config, the SentencePiece model, and the
   * external weights of an ONNX export that splits them out (as bge-m3 does). */
  private static final String[] OPTIONAL_FILES = {"tokenizer_config.json",
      "sentencepiece.bpe.model", "onnx/model.onnx_data"};

  /** Not instantiable. */
  private HuggingFaceModelCache() {
  }

  /**
   * Resolves a teacher reference to a local directory holding its files.
   *
   * @param teacher  A local directory, used as-is, or a Hugging Face model id
   *                 ({@code org/model}), downloaded into
   *                 {@code ~/.cache/opennlp-embeddings/<org--model>} on first use. Must not be
   *                 {@code null}.
   * @param listener Receives one progress line per download; may be {@code null}.
   * @return The local teacher directory.
   * @throws IllegalArgumentException Thrown if {@code teacher} is {@code null}, a local path
   *     that is not a directory, or a hub id whose required files cannot be downloaded.
   */
  static Path resolve(String teacher, ModelDistiller.ProgressListener listener) {
    if (teacher == null) {
      throw new IllegalArgumentException("Teacher must not be null");
    }
    final Path local = Path.of(teacher);
    if (Files.isDirectory(local)) {
      return local;
    }
    if (!teacher.matches("[\\w.-]+/[\\w.-]+")) {
      throw new IllegalArgumentException("Teacher '" + teacher + "' is neither a local "
          + "directory nor a Hugging Face model id (expected 'org/model')");
    }
    final Path cache = Path.of(System.getProperty("user.home"), ".cache", "opennlp-embeddings",
        teacher.replace('/', '-').replace(".", "_"));
    final HttpClient client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(30))
        .build();
    for (final String file : REQUIRED_FILES) {
      download(client, teacher, file, cache, true, listener);
    }
    for (final String file : OPTIONAL_FILES) {
      download(client, teacher, file, cache, false, listener);
    }
    return cache;
  }

  /**
   * Downloads one repository file into the cache, skipping files already there.
   *
   * @param client   The HTTP client.
   * @param modelId  The hub model id.
   * @param file     The repository-relative file name.
   * @param cache    The cache directory.
   * @param required Whether a missing file is an error.
   * @param listener The progress listener; may be {@code null}.
   * @throws IllegalArgumentException Thrown if a required file cannot be downloaded.
   */
  private static void download(HttpClient client, String modelId, String file, Path cache,
                               boolean required, ModelDistiller.ProgressListener listener) {
    final Path target = cache.resolve(file);
    if (Files.isRegularFile(target)) {
      return;
    }
    final HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(RESOLVE_BASE + modelId + "/resolve/main/" + file))
        .timeout(Duration.ofHours(1))
        .GET()
        .build();
    final HttpResponse<InputStream> response;
    try {
      response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to download " + file + " of " + modelId + ": "
          + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalArgumentException("Interrupted while downloading " + file + " of "
          + modelId, e);
    }
    if (response.statusCode() != 200) {
      if (required) {
        throw new IllegalArgumentException("Failed to download " + file + " of " + modelId
            + ": HTTP " + response.statusCode() + "; the distillation needs this file");
      }
      return;
    }
    try {
      if (listener != null) {
        listener.progress("Downloading " + modelId + "/" + file + " ...");
      }
      Files.createDirectories(target.getParent());
      final Path temporary = target.resolveSibling(target.getFileName() + ".download");
      try (InputStream body = response.body()) {
        Files.copy(body, temporary, StandardCopyOption.REPLACE_EXISTING);
      }
      Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to store " + file + " of " + modelId + " at "
          + target + ": " + e.getMessage(), e);
    }
  }
}
