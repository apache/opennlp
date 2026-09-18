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
package opennlp.tools.util.ext;

import java.io.Serial;

/**
 * Thrown if no available {@link Provider provider} supports a {@link ProviderSpec spec}, or none
 * of them has the configured name.
 *
 * @see Providers#select(ProviderSpec)
 * @since 3.0.0
 */
public class UnsatisfiedProviderException extends ProviderResolutionException {

  @Serial
  private static final long serialVersionUID = -723155816183550334L;

  /**
   * Initializes the exception.
   *
   * @param message The detail message.
   */
  public UnsatisfiedProviderException(final String message) {
    super(message);
  }
}
