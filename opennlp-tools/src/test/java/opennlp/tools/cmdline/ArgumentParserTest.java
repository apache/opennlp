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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.cmdline.ArgumentParser.OptionalParameter;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.params.EncodingParameter;

public class ArgumentParserTest {

  interface ZeroMethods {
  }

  @Test
  void testZeroMethods() {
    Assertions.assertThrows(IllegalArgumentException.class, () ->
            ArgumentParser.createUsage(ZeroMethods.class));
  }

  interface InvalidMethodName {
    String invalidMethodName();
  }

  @Test
  void testInvalidMethodName() {
    Assertions.assertThrows(IllegalArgumentException.class, () ->
            ArgumentParser.createUsage(InvalidMethodName.class));
  }

  interface InvalidReturnType {
    Exception getTest();
  }

  @Test
  void testInvalidReturnType() {
    Assertions.assertThrows(IllegalArgumentException.class, () ->
            ArgumentParser.createUsage(InvalidReturnType.class));
  }

  interface SimpleArguments extends AllOptionalArguments {

    @ParameterDescription(valueName = "charset", description = "a charset encoding")
    String getEncoding();

    @OptionalParameter
    Integer getCutoff();
  }

  interface AllOptionalArguments {

    @ParameterDescription(valueName = "num")
    @OptionalParameter(defaultValue = "100")
    Integer getIterations();

    @ParameterDescription(valueName = "true|false")
    @OptionalParameter(defaultValue = "true")
    Boolean getAlphaNumOpt();
  }


  @Test
  void testSimpleArguments() {
    String argsString = "-encoding UTF-8 -alphaNumOpt false";
    SimpleArguments args = ArgumentParser.parse(argsString.split(" "), SimpleArguments.class);

    Assertions.assertEquals(StandardCharsets.UTF_8.name(), args.getEncoding());
    Assertions.assertEquals(Integer.valueOf(100), args.getIterations());
    Assertions.assertNull(args.getCutoff());
    Assertions.assertEquals(false, args.getAlphaNumOpt());
  }

  @Test
  void testSimpleArgumentsMissingEncoding() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      String argsString = "-alphaNumOpt false";

      Assertions.assertFalse(ArgumentParser.validateArguments(argsString.split(" "), SimpleArguments.class));
      ArgumentParser.parse(argsString.split(" "), SimpleArguments.class);
    });

  }

  @Test
  void testAllOptionalArgumentsOneArgument() {
    String argsString = "-alphaNumOpt false";

    Assertions.assertTrue(ArgumentParser.validateArguments(argsString.split(" "),
        AllOptionalArguments.class));
    ArgumentParser.parse(argsString.split(" "), AllOptionalArguments.class);
  }

  @Test
  void testAllOptionalArgumentsZeroArguments() {
    String[] args = {};
    Assertions.assertTrue(ArgumentParser.validateArguments(args, AllOptionalArguments.class));
    ArgumentParser.parse(args, AllOptionalArguments.class);
  }

  @Test
  void testAllOptionalArgumentsExtraArgument() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      String argsString = "-encoding UTF-8";
      Assertions.assertFalse(ArgumentParser.validateArguments(argsString.split(" "),
          AllOptionalArguments.class));
      ArgumentParser.parse(argsString.split(" "), AllOptionalArguments.class);
    });
  }

  @Test
  void testSimpleArgumentsUsage() {

    String[] arguments = new String[] {"-encoding charset",
        "[-iterations num]",
        "[-alphaNumOpt true|false]"};

    String usage = ArgumentParser.createUsage(SimpleArguments.class);

    int expectedLength = 2;
    for (String arg : arguments) {
      Assertions.assertTrue(usage.contains(arg));
      expectedLength += arg.length();
    }

    Assertions.assertTrue(usage.contains("a charset encoding"));
    Assertions.assertTrue(expectedLength < usage.length());
  }

  interface ExtendsEncodingParameter extends EncodingParameter {
    @ParameterDescription(valueName = "value")
    String getSomething();
  }

  @Test
  void testDefaultEncodingParameter() {

    String[] args = "-something aValue".split(" ");
    Assertions.assertTrue(ArgumentParser.validateArguments(args, ExtendsEncodingParameter.class));

    ExtendsEncodingParameter params = ArgumentParser.parse(args, ExtendsEncodingParameter.class);
    Assertions.assertEquals(Charset.defaultCharset(), params.getEncoding());
  }

  @Test
  void testSetEncodingParameter() {
    Collection<Charset> availableCharset = Charset.availableCharsets().values();
    String notTheDefaultCharset = StandardCharsets.UTF_8.name();
    for (Charset charset : availableCharset) {
      if (!charset.equals(Charset.defaultCharset())) {
        notTheDefaultCharset = charset.name();
        break;
      }
    }

    String[] args = ("-something aValue -encoding " + notTheDefaultCharset).split(" ");
    Assertions.assertTrue(ArgumentParser.validateArguments(args, ExtendsEncodingParameter.class));

    ExtendsEncodingParameter params = ArgumentParser.parse(args, ExtendsEncodingParameter.class);
    Assertions.assertEquals(Charset.forName(notTheDefaultCharset), params.getEncoding());
  }
}
