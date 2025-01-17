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

package opennlp.tools;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import static org.junit.platform.commons.util.AnnotationUtils.findAnnotation;

/**
 * A custom JUnit5 conditional annotation which can be used to enable/disable tests at runtime.
 */
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(EnabledWhenCDNAvailable.CDNAvailableCondition.class)
public @interface EnabledWhenCDNAvailable {

  String hostname();

  int TIMEOUT_MS = 2000;

  // JUnit5 execution condition to decide whether tests can assume CDN downloads are possible (= online).
  class CDNAvailableCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
      final var optional = findAnnotation(context.getElement(), EnabledWhenCDNAvailable.class);
      if (optional.isPresent()) {
        final EnabledWhenCDNAvailable annotation = optional.get();
        final String host = annotation.hostname();
        try (Socket socket = new Socket()) {
          // First, try to establish a socket connection to ensure the host is reachable
          socket.connect(new InetSocketAddress(host, 443), TIMEOUT_MS);

          // Then, try to check the HTTP status by making an HTTPS request
          final URL url = new URL("https://" + host);
          final HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
          connection.setConnectTimeout(TIMEOUT_MS);
          connection.setReadTimeout(TIMEOUT_MS);
          int statusCode = connection.getResponseCode();

          // If the HTTP status code indicates success (2xx range), return enabled
          if (statusCode >= 200 && statusCode < 300) {
            return ConditionEvaluationResult.enabled(
                "Resource (CDN) reachable with status code: " + statusCode);
          } else {
            return ConditionEvaluationResult.disabled(
                "Resource (CDN) reachable, but HTTP status code: " + statusCode);

          }
        } catch (IOException e) {
          return ConditionEvaluationResult.disabled(
              "Resource (CDN) unreachable.");
        }
      }
      return ConditionEvaluationResult.enabled(
          "Nothing annotated with EnabledWhenCDNAvailable.");
    }
  }

}
