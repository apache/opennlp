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
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Fetches the files a distillation needs from a Hugging Face model repository into a local cache
 * directory, so a teacher can be named by its hub id ({@code org/model}) instead of a local path.
 * Files download once and are reused afterwards; a file the repository does not have (a 404, e.g.
 * a WordPiece teacher's {@code sentencepiece.bpe.model}) is reported as absent, not an error.
 */
final class HuggingFaceModelCache {

  /** The hub's host, the prefix of every download URL. */
  private static final String HUB_BASE = "https://huggingface.co/";

  /** The hub's download path between the model id and the repository-relative file name. */
  private static final String RESOLVE_PATH = "/resolve/main/";

  /** A hub model id: an organization and a model name, both of word characters, dots, or dashes. */
  private static final Pattern MODEL_ID_PATTERN = Pattern.compile("[\\w.-]+/[\\w.-]+");

  /** The directory the cache lives in, below the user's home directory. */
  private static final String CACHE_DIRECTORY = ".cache";

  /** The cache's own directory, below {@link #CACHE_DIRECTORY}. */
  private static final String CACHE_NAME = "opennlp-embeddings";

  /** The suffix of the temporary file a download streams into before it is moved into place. */
  private static final String DOWNLOAD_SUFFIX = ".download";

  /** The HTTP status a served file answers with; anything else means the file is not there. */
  private static final int HTTP_OK = 200;

  /** How long the client waits for a connection to the hub. */
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);

  /** How long a single file download may take; an ONNX graph can be gigabytes. */
  private static final Duration DOWNLOAD_TIMEOUT = Duration.ofHours(1);

  /** The files a distillation needs, relative to the repository root. */
  private static final List<String> REQUIRED_FILES =
      List.of(ModelFileNames.TOKENIZER_JSON, ModelFileNames.ONNX_MODEL);

  /**
   * The files used when present: the pad-token config, the trained SentencePiece model under any
   * of the names a repository may ship it as, and the external weights of an ONNX export that
   * splits them out (as bge-m3 does).
   */
  private static final List<String> OPTIONAL_FILES = optionalFiles();

  /** Not instantiable. */
  private HuggingFaceModelCache() {
  }

  /** {@return the repository-relative names of the files downloaded when the repository has them} */
  private static List<String> optionalFiles() {
    final List<String> files = new ArrayList<>();
    files.add(ModelFileNames.TOKENIZER_CONFIG);
    files.addAll(ModelFileNames.SENTENCEPIECE_MODELS);
    files.add(ModelFileNames.ONNX_MODEL_DATA);
    return List.copyOf(files);
  }

  /**
   * Resolves a teacher reference to a local directory holding its files.
   *
   * @param teacher  A local directory, used as-is, or a Hugging Face model id
   *                 ({@code org/model}), downloaded into
   *                 {@code ~/.cache/opennlp-embeddings/org-model} on first use (the slash becomes
   *                 a dash and dots become underscores). Must not be {@code null}.
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
    if (!MODEL_ID_PATTERN.matcher(teacher).matches()) {
      throw new IllegalArgumentException("Teacher '" + teacher + "' is neither a local "
          + "directory nor a Hugging Face model id (expected 'org/model')");
    }
    final Path cache = Path.of(System.getProperty("user.home"), CACHE_DIRECTORY, CACHE_NAME,
        teacher.replace('/', '-').replace('.', '_'));
    // A client built through the builder has no proxy selector unless one is set, so the
    // http.proxyHost / https.proxyHost system properties would otherwise be ignored.
    final HttpClient client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .proxy(ProxySelector.getDefault())
        .connectTimeout(CONNECT_TIMEOUT)
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
        .uri(URI.create(HUB_BASE + modelId + RESOLVE_PATH + file))
        .timeout(DOWNLOAD_TIMEOUT)
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
    Path temporary = null;
    try (InputStream body = response.body()) {
      if (response.statusCode() != HTTP_OK) {
        if (required) {
          throw new IllegalArgumentException("Failed to download " + file + " of " + modelId
              + ": HTTP " + response.statusCode() + "; the distillation needs this file");
        }
        return;
      }
      if (listener != null) {
        listener.progress("Downloading " + modelId + "/" + file + " ...");
      }
      Files.createDirectories(target.getParent());
      // A temporary name unique per download: two processes sharing one cache directory must not
      // stream two copies of the same file into one partial file and publish the interleaving.
      temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(),
          DOWNLOAD_SUFFIX);
      Files.copy(body, temporary, StandardCopyOption.REPLACE_EXISTING);
      Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      temporary = null;
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to store " + file + " of " + modelId + " at "
          + target + ": " + e.getMessage(), e);
    } finally {
      deleteIfPresent(temporary);
    }
  }

  /**
   * Deletes a partial download, if there is one, without reporting a failure to do so.
   *
   * @param file The file to delete; may be {@code null}.
   */
  private static void deleteIfPresent(Path file) {
    if (file == null) {
      return;
    }
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      // A leftover partial download costs disk space; the next attempt writes a fresh file.
    }
  }
}
