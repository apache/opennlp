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

package opennlp.tools.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static opennlp.tools.util.InstallerTestSupport.KIBIBYTE;
import static opennlp.tools.util.InstallerTestSupport.MEBIBYTE;
import static opennlp.tools.util.InstallerTestSupport.installedFiles;
import static opennlp.tools.util.InstallerTestSupport.sha256;
import static opennlp.tools.util.InstallerTestSupport.tarGz;

/**
 * Exercises {@link ResourceInstaller} against a local scripted HTTP server: happy
 * downloads, redirect handling and its policy, error statuses, stalled responses
 * against the read timeout, and download limits against incorrect or oversized bodies.
 */
public class ResourceInstallerHttpTest {

  private static final Duration GENEROUS = Duration.ofSeconds(10);

  /** The limits these tests never exercise, kept at their defaults. */
  private static final long DEFAULT_ENTRIES =
      ResourceInstaller.Limits.DEFAULT.maxEntries();
  private static final long DEFAULT_RATIO =
      ResourceInstaller.Limits.DEFAULT.maxExpansionRatio();

  /** Long enough that a stalled route outlives any timeout a test configures. */
  private static final Duration STALL = Duration.ofSeconds(30);

  /**
   * A well-formed digest for fetches that fail before verification runs; its value is
   * never compared.
   */
  private static final String UNREACHED_CHECKSUM = "0".repeat(64);

  private StubServer server;

  @BeforeEach
  void startServer() throws IOException {
    server = new StubServer();
  }

  @AfterEach
  void stopServer() throws IOException {
    server.close();
  }

  /**
   * Builds installation limits with the given read timeout and otherwise generous
   * values, so timeout tests state only the value they exercise.
   *
   * @param readTimeout The read timeout to apply.
   * @return The limits. Never {@code null}.
   */
  private static ResourceInstaller.Limits withReadTimeout(Duration readTimeout) {
    return new ResourceInstaller.Limits(GENEROUS, readTimeout, 5, MEBIBYTE, MEBIBYTE,
        DEFAULT_ENTRIES, DEFAULT_RATIO);
  }

  /**
   * Builds installation limits with the given redirect allowance and otherwise
   * generous values.
   *
   * @param maxRedirects The number of redirects to follow.
   * @return The limits. Never {@code null}.
   */
  private static ResourceInstaller.Limits withMaxRedirects(int maxRedirects) {
    return new ResourceInstaller.Limits(GENEROUS, GENEROUS, maxRedirects,
        MEBIBYTE, MEBIBYTE, DEFAULT_ENTRIES, DEFAULT_RATIO);
  }

  /**
   * Builds installation limits with the given download limit and otherwise generous
   * values.
   *
   * @param maxDownloadBytes The download limit in bytes.
   * @return The limits. Never {@code null}.
   */
  private static ResourceInstaller.Limits withDownloadLimit(long maxDownloadBytes) {
    return new ResourceInstaller.Limits(GENEROUS, GENEROUS, 5, maxDownloadBytes,
        MEBIBYTE, DEFAULT_ENTRIES, DEFAULT_RATIO);
  }

  /**
   * Asserts that the given call is rejected as an argument error demanding a checksum
   * for the remote source.
   *
   * @param source The remote source the message must name.
   * @param call The call under test.
   */
  private static void assertChecksumRequired(URI source, Executable call) {
    final IllegalArgumentException thrown =
        Assertions.assertThrows(IllegalArgumentException.class, call);
    Assertions.assertEquals(
        "checksum must be given for an http or https source: " + source,
        thrown.getMessage());
  }

  @Test
  void testHttpSourceWithoutChecksumIsRejectedBeforeFetching(@TempDir Path target)
      throws Exception {
    final AtomicBoolean fetched = new AtomicBoolean();
    server.route("/corpus.tar.gz", out -> {
      fetched.set(true);
      StubServer.ok(out, tarGz(new String[][] {{"corpus/data.txt", "unverified"}}));
    });

    final URI source = server.uri("/corpus.tar.gz");
    Assertions.assertAll(
        () -> assertChecksumRequired(source,
            () -> ResourceInstaller.install(source, target)),
        () -> assertChecksumRequired(source,
            () -> ResourceInstaller.install(source, target, null)),
        () -> assertChecksumRequired(source,
            () -> ResourceInstaller.install(source, target, null, withMaxRedirects(5))));
    Assertions.assertFalse(fetched.get());
    Assertions.assertEquals(List.of(), installedFiles(target));
  }

  @Test
  void testHttpsSourceWithoutChecksumIsRejectedBeforeCreatingTheTarget(
      @TempDir Path parent) {
    final Path target = parent.resolve("not-created-yet");
    final URI source = URI.create("https://example.invalid/corpus.tar.gz");

    assertChecksumRequired(source, () -> ResourceInstaller.install(source, target));
    Assertions.assertTrue(Files.notExists(target));
  }

  @Test
  void testHttpDownloadInstallsArchive(@TempDir Path target) throws Exception {
    final byte[] archive = tarGz(new String[][] {{"corpus/data.txt", "over http"}});
    server.route("/corpus.tar.gz", out -> StubServer.ok(out, archive));

    ResourceInstaller.install(server.uri("/corpus.tar.gz"), target, sha256(archive));

    Assertions.assertEquals("over http",
        Files.readString(target.resolve("corpus/data.txt")));
  }

  @Test
  void testInvalidSourceNameIsRejectedBeforeFetching(@TempDir Path parent)
      throws Exception {
    final AtomicBoolean fetched = new AtomicBoolean();
    final byte[] content = "dictionary".getBytes(StandardCharsets.UTF_8);
    server.route("/bad%00name.dat", out -> {
      fetched.set(true);
      StubServer.ok(out, content);
    });
    final Path target = parent.resolve("not-created-yet");

    final IllegalArgumentException thrown = Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> ResourceInstaller.install(server.uri("/bad%00name.dat"), target,
            sha256(content)));

    Assertions.assertEquals("name must be a file name", thrown.getMessage());
    Assertions.assertFalse(fetched.get());
    Assertions.assertTrue(Files.notExists(target));
  }

  @Test
  void testAbsoluteRedirectIsFollowed(@TempDir Path target) throws Exception {
    final byte[] archive = tarGz(new String[][] {{"corpus/data.txt", "moved"}});
    server.route("/old.tar.gz", out -> StubServer.redirect(out,
        server.uri("/new.tar.gz").toString()));
    server.route("/new.tar.gz", out -> StubServer.ok(out, archive));

    ResourceInstaller.install(server.uri("/old.tar.gz"), target, sha256(archive));

    Assertions.assertEquals("moved",
        Files.readString(target.resolve("corpus/data.txt")));
  }

  @Test
  void testRelativeRedirectIsResolvedAgainstTheSource(@TempDir Path target)
      throws Exception {
    final byte[] archive = tarGz(new String[][] {{"corpus/data.txt", "relative"}});
    server.route("/mirror/old.tar.gz",
        out -> StubServer.redirect(out, "new.tar.gz"));
    server.route("/mirror/new.tar.gz", out -> StubServer.ok(out, archive));

    ResourceInstaller.install(server.uri("/mirror/old.tar.gz"), target, sha256(archive));

    Assertions.assertEquals("relative",
        Files.readString(target.resolve("corpus/data.txt")));
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"301 Moved Permanently", "302 Found", "303 See Other",
      "307 Temporary Redirect", "308 Permanent Redirect"})
  void testEveryRedirectStatusIsFollowed(String status, @TempDir Path target)
      throws Exception {
    final byte[] archive = tarGz(new String[][] {{"corpus/data.txt", "followed"}});
    server.route("/old.tar.gz", out -> StubServer.redirect(out, status, "/new.tar.gz"));
    server.route("/new.tar.gz", out -> StubServer.ok(out, archive));

    ResourceInstaller.install(server.uri("/old.tar.gz"), target, sha256(archive));

    Assertions.assertEquals("followed",
        Files.readString(target.resolve("corpus/data.txt")));
  }

  @Test
  void testZeroRedirectAllowanceRejectsTheFirstRedirect(@TempDir Path target)
      throws Exception {
    server.route("/once", out -> StubServer.redirect(out,
        server.uri("/anywhere.tar.gz").toString()));

    final URI source = server.uri("/once");
    final IOException thrown = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(source, target, UNREACHED_CHECKSUM, withMaxRedirects(0)));
    Assertions.assertEquals("more than 0 redirects: " + source, thrown.getMessage());
    Assertions.assertEquals(List.of(), installedFiles(target));
  }

  @Test
  void testMalformedRedirectLocationFails() {
    final URI from = URI.create("http://example.invalid/archive.tar.gz");

    final IOException thrown = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.resolveRedirect(from, "http://mirror.invalid/bad path"));
    Assertions.assertEquals("redirect from " + from
        + " contains a malformed Location: http://mirror.invalid/bad path",
        thrown.getMessage());
  }

  @Test
  void testRedirectChainBeyondLimitFails(@TempDir Path target) throws Exception {
    server.route("/loop", out -> StubServer.redirect(out, "/loop"));

    final URI source = server.uri("/loop");
    final IOException thrown = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(source, target, UNREACHED_CHECKSUM, withMaxRedirects(3)));
    Assertions.assertEquals("more than 3 redirects: " + source, thrown.getMessage());
    Assertions.assertEquals(List.of(), installedFiles(target));
  }

  @Test
  void testRedirectWithoutLocationFails(@TempDir Path target) throws Exception {
    server.route("/broken", out -> StubServer.head(out, "302 Found",
        "Content-Length: 4", "", "gone"));

    final URI source = server.uri("/broken");
    final IOException thrown = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(source, target, UNREACHED_CHECKSUM));
    Assertions.assertEquals("redirect from " + source + " contains no Location header",
        thrown.getMessage());
    Assertions.assertEquals(List.of(), installedFiles(target));
  }

  @Test
  void testRedirectToNonHttpSchemeFails(@TempDir Path target) throws Exception {
    server.route("/non-http", out -> StubServer.redirect(out, "file:///etc/passwd"));

    final IOException thrown = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(server.uri("/non-http"), target,
            UNREACHED_CHECKSUM));
    Assertions.assertEquals(
        "redirect target is not an http or https location: file:///etc/passwd",
        thrown.getMessage());
    Assertions.assertEquals(List.of(), installedFiles(target));
  }

  @Test
  void testHttpsToHttpDowngradeIsRejected() {
    final URI https = URI.create("https://example.invalid/archive.tar.gz");

    // Cover the rejected downgrade and both accepted upgrade and same-scheme cases.
    Assertions.assertAll(
        () -> {
          final IOException thrown = Assertions.assertThrows(IOException.class,
              () -> ResourceInstaller.resolveRedirect(https,
                  "http://example.invalid/archive.tar.gz"));
          Assertions.assertEquals(
              "redirect downgrades https to http: http://example.invalid/archive.tar.gz",
              thrown.getMessage());
        },
        () -> Assertions.assertDoesNotThrow(() -> ResourceInstaller.resolveRedirect(https,
            "https://mirror.invalid/archive.tar.gz")),
        () -> Assertions.assertDoesNotThrow(() -> ResourceInstaller.resolveRedirect(
            URI.create("http://example.invalid/archive.tar.gz"),
            "https://mirror.invalid/archive.tar.gz")));
  }

  @Test
  void testHttpErrorStatusFails(@TempDir Path target) throws Exception {
    server.route("/missing.tar.gz", out -> StubServer.head(out, "404 Not Found",
        "Content-Length: 0", ""));

    final URI source = server.uri("/missing.tar.gz");
    final IOException thrown = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(source, target, UNREACHED_CHECKSUM));
    Assertions.assertEquals("download failed with HTTP status 404: " + source,
        thrown.getMessage());
    Assertions.assertEquals(List.of(), installedFiles(target));
  }

  @Test
  void testStalledResponseHitsReadTimeout(@TempDir Path target) {
    server.route("/stall", out -> StubServer.sleep(STALL));

    final IOException thrown = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(server.uri("/stall"), target,
            UNREACHED_CHECKSUM, withReadTimeout(Duration.ofMillis(250))));
    Assertions.assertInstanceOf(SocketTimeoutException.class, thrown);
  }

  @Test
  void testStalledBodyHitsReadTimeout(@TempDir Path target) throws Exception {
    server.route("/drip", out -> {
      StubServer.head(out, "200 OK", "Content-Length: 100000", "");
      out.write("just a few bytes".getBytes(StandardCharsets.UTF_8));
      out.flush();
      StubServer.sleep(STALL);
    });

    final IOException thrown = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(server.uri("/drip"), target,
            UNREACHED_CHECKSUM, withReadTimeout(Duration.ofMillis(250))));
    Assertions.assertInstanceOf(SocketTimeoutException.class, thrown);
    Assertions.assertEquals(List.of(), installedFiles(target));
  }

  @Test
  void testDeclaredContentLengthBeyondLimitFailsFast(@TempDir Path target)
      throws Exception {
    server.route("/liar.tar.gz", out -> StubServer.head(out, "200 OK",
        "Content-Length: 10000000", ""));

    final IOException thrown = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(server.uri("/liar.tar.gz"), target,
            UNREACHED_CHECKSUM, withDownloadLimit(KIBIBYTE)));
    Assertions.assertEquals(
        "declared content length 10000000 exceeds the download limit of 1024 bytes",
        thrown.getMessage());
    Assertions.assertEquals(List.of(), installedFiles(target));
  }

  @Test
  void testStreamedBodyBeyondLimitAborts(@TempDir Path target) throws Exception {
    server.route("/endless", out -> {
      StubServer.head(out, "200 OK", "");
      out.write(new byte[64 * 1024]);
      out.flush();
    });

    final IOException thrown = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(server.uri("/endless"), target,
            UNREACHED_CHECKSUM, withDownloadLimit(KIBIBYTE)));
    Assertions.assertEquals("download exceeds the limit of 1024 bytes",
        thrown.getMessage());
    Assertions.assertEquals(List.of(), installedFiles(target));
  }

  @Test
  @Timeout(value = 15, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
  void testSubMillisecondReadTimeoutStillTimesOut(@TempDir Path target) {
    server.route("/stall", out -> StubServer.sleep(STALL));
    final ResourceInstaller.Limits limits = ResourceInstaller.Limits.builder()
        .readTimeout(Duration.ofNanos(1))
        .build();

    final IOException thrown = Assertions.assertThrows(IOException.class,
        () -> ResourceInstaller.install(server.uri("/stall"), target,
            UNREACHED_CHECKSUM, limits));

    Assertions.assertInstanceOf(SocketTimeoutException.class, thrown);
  }


  @Test
  void testTimeoutBeyondTheMillisecondRangeIsCapped(@TempDir Path target)
      throws Exception {
    final byte[] archive = tarGz(new String[][] {{"payload/data.txt", "content"}});
    server.route("/payload.tar.gz", out -> StubServer.ok(out, archive));
    final Duration beyondMillis = Duration.ofSeconds(Long.MAX_VALUE / 1000 + 1);
    final ResourceInstaller.Limits limits = ResourceInstaller.Limits.builder()
        .connectTimeout(beyondMillis)
        .readTimeout(beyondMillis)
        .build();

    ResourceInstaller.install(server.uri("/payload.tar.gz"), target, sha256(archive),
        limits);

    Assertions.assertEquals("content",
        Files.readString(target.resolve("payload/data.txt")));
  }

  /**
   * A scripted HTTP server on a loopback socket. Each registered route writes a raw
   * response for redirect, timeout, malformed-response, and size-limit tests.
   */
  private static final class StubServer implements AutoCloseable {

    /** Writes a raw HTTP response to the connected client. */
    @FunctionalInterface
    interface Responder {

      /**
       * Writes the raw response bytes for one request.
       *
       * @param out The response stream.
       * @throws IOException Thrown if writing fails.
       */
      void respond(OutputStream out) throws IOException;
    }

    private final ServerSocket socket;
    private final Map<String, Responder> routes = new ConcurrentHashMap<>();
    private final List<Socket> connections = new ArrayList<>();

    StubServer() throws IOException {
      socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
      final Thread acceptor = new Thread(this::acceptLoop, "stub-http-acceptor");
      acceptor.setDaemon(true);
      acceptor.start();
    }

    /**
     * Registers the responder serving the given absolute request path.
     *
     * @param path The absolute request path, starting with {@code /}.
     * @param responder The script producing the raw response.
     */
    void route(String path, Responder responder) {
      routes.put(path, responder);
    }

    /**
     * Builds the http URI of the given absolute path on this server.
     *
     * @param path The absolute request path, starting with {@code /}.
     * @return The URI. Never {@code null}.
     */
    URI uri(String path) {
      return URI.create("http://127.0.0.1:" + socket.getLocalPort() + path);
    }

    /**
     * Writes the HTTP/1.0 status line followed by the given lines, each terminated by
     * CRLF. The caller supplies the header lines, then an empty string for the blank
     * line separating head from body, then optional body text.
     *
     * @param out The response stream.
     * @param status The status line content after the protocol, such as {@code 200 OK}.
     * @param lines Header lines, then an empty string separator, then optional body
     *              text, each written with a trailing CRLF.
     * @throws IOException Thrown if writing fails.
     */
    static void head(OutputStream out, String status, String... lines)
        throws IOException {
      final StringBuilder response = new StringBuilder("HTTP/1.0 ").append(status)
          .append("\r\n");
      for (final String line : lines) {
        response.append(line).append("\r\n");
      }
      out.write(response.toString().getBytes(StandardCharsets.US_ASCII));
      out.flush();
    }

    /**
     * Writes a complete 200 response carrying the given body with its exact length.
     *
     * @param out The response stream.
     * @param body The response body bytes.
     * @throws IOException Thrown if writing fails.
     */
    static void ok(OutputStream out, byte[] body) throws IOException {
      head(out, "200 OK", "Content-Length: " + body.length, "");
      out.write(body);
      out.flush();
    }

    /**
     * Writes a 302 redirect to the given location.
     *
     * @param out The response stream.
     * @param location The Location header value, absolute or relative.
     * @throws IOException Thrown if writing fails.
     */
    static void redirect(OutputStream out, String location) throws IOException {
      redirect(out, "302 Found", location);
    }

    /**
     * Writes a redirect with the given status to the given location.
     *
     * @param out The response stream.
     * @param status The status line content after the protocol, such as
     *               {@code 301 Moved Permanently}.
     * @param location The Location header value, absolute or relative.
     * @throws IOException Thrown if writing fails.
     */
    static void redirect(OutputStream out, String status, String location)
        throws IOException {
      head(out, status, "Location: " + location, "Content-Length: 0", "");
    }

    /**
     * Blocks the handler thread, simulating a stalled server. Interruption during
     * teardown ends the sleep early.
     *
     * @param duration How long to stall.
     */
    static void sleep(Duration duration) {
      try {
        Thread.sleep(duration.toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    private void acceptLoop() {
      while (!socket.isClosed()) {
        final Socket connection;
        try {
          connection = socket.accept();
        } catch (IOException e) {
          return;
        }
        synchronized (connections) {
          connections.add(connection);
        }
        final Thread handler = new Thread(() -> handle(connection),
            "stub-http-handler");
        handler.setDaemon(true);
        handler.start();
      }
    }

    /**
     * Reads one request, dispatches it to the registered responder, and closes the
     * connection. Write failures from clients that abort mid-transfer are expected
     * and ignored.
     *
     * @param connection The accepted client connection.
     */
    private void handle(Socket connection) {
      try (connection) {
        final String path = readRequestPath(connection.getInputStream());
        final Responder responder = routes.get(path);
        if (responder == null) {
          head(connection.getOutputStream(), "404 Not Found", "Content-Length: 0", "");
          return;
        }
        responder.respond(connection.getOutputStream());
      } catch (IOException e) {
        // The client hung up or the server is shutting down; both are test-normal.
      }
    }

    /**
     * Reads the request head up to its terminating blank line and returns the path
     * from the request line.
     *
     * @param in The request stream.
     * @return The request path. Never {@code null}.
     * @throws IOException Thrown if the request head is malformed or truncated.
     */
    private static String readRequestPath(InputStream in) throws IOException {
      final ByteArrayOutputStream headBytes = new ByteArrayOutputStream();
      int last4 = 0;
      int b;
      while ((b = in.read()) >= 0) {
        headBytes.write(b);
        last4 = (last4 << 8) | b;
        if (last4 == 0x0D0A0D0A) {
          break;
        }
      }
      final String head = headBytes.toString(StandardCharsets.US_ASCII);
      final int firstLineEnd = head.indexOf("\r\n");
      final String requestLine = firstLineEnd < 0 ? head : head.substring(0, firstLineEnd);
      final String[] parts = requestLine.split(" ");
      if (parts.length < 2) {
        throw new IOException("malformed request line: " + requestLine);
      }
      return parts[1];
    }

    @Override
    public void close() throws IOException {
      socket.close();
      synchronized (connections) {
        for (final Socket connection : connections) {
          connection.close();
        }
      }
    }
  }
}
