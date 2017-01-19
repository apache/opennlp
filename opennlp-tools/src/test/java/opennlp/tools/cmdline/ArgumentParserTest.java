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
import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.cmdline.ArgumentParser.OptionalParameter;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.params.EncodingParameter;

public class ArgumentParserTest {

  interface ZeroMethods {
  }

  @Test(expected = IllegalArgumentException.class)
  public void testZeroMethods() {
    ArgumentParser.createUsage(ZeroMethods.class);
  }

  interface InvalidMethodName {
    String invalidMethodName();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidMethodName() {
    ArgumentParser.createUsage(InvalidMethodName.class);
  }

  interface InvalidReturnType {
    Exception getTest();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidReturnType() {
    ArgumentParser.createUsage(InvalidReturnType.class);
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
  public void testSimpleArguments() {
    String argsString = "-encoding UTF-8 -alphaNumOpt false";
    SimpleArguments args = ArgumentParser.parse(argsString.split(" "), SimpleArguments.class);

    Assert.assertEquals("UTF-8", args.getEncoding());
    Assert.assertEquals(Integer.valueOf(100), args.getIterations());
    Assert.assertEquals(null, args.getCutoff());
    Assert.assertEquals(false, args.getAlphaNumOpt());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSimpleArgumentsMissingEncoding() {
    String argsString = "-alphaNumOpt false";

    Assert.assertFalse(ArgumentParser.validateArguments(argsString.split(" "), SimpleArguments.class));
    ArgumentParser.parse(argsString.split(" "), SimpleArguments.class);
  }

  @Test
  public void testAllOptionalArgumentsOneArgument() {
    String argsString = "-alphaNumOpt false";

    Assert.assertTrue(ArgumentParser.validateArguments(argsString.split(" "), AllOptionalArguments.class));
    ArgumentParser.parse(argsString.split(" "), AllOptionalArguments.class);
  }

  @Test
  public void testAllOptionalArgumentsZeroArguments() {
    String[] args = {};
    Assert.assertTrue(ArgumentParser.validateArguments(args, AllOptionalArguments.class));
    ArgumentParser.parse(args, AllOptionalArguments.class);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testAllOptionalArgumentsExtraArgument() {
    String argsString = "-encoding UTF-8";
    Assert.assertFalse(ArgumentParser.validateArguments(argsString.split(" "), AllOptionalArguments.class));
    ArgumentParser.parse(argsString.split(" "), AllOptionalArguments.class);
  }

  @Test
  public void testSimpleArgumentsUsage() {

    String arguments[] = new String[] {"-encoding charset",
        "[-iterations num]",
        "[-alphaNumOpt true|false]"};

    String usage = ArgumentParser.createUsage(SimpleArguments.class);

    int expectedLength = 2;
    for (String arg : arguments) {
      Assert.assertTrue(usage.contains(arg));
      expectedLength += arg.length();
    }

    Assert.assertTrue(usage.contains("a charset encoding"));
    Assert.assertTrue(expectedLength < usage.length());
  }

  interface ExtendsEncodingParameter extends EncodingParameter {
    @ParameterDescription(valueName = "value")
    String getSomething();
  }

  @Test
  public void testDefaultEncodingParameter() {

    String args[] = "-something aValue".split(" ");
    Assert.assertTrue(ArgumentParser.validateArguments(args, ExtendsEncodingParameter.class));

    ExtendsEncodingParameter params = ArgumentParser.parse(args, ExtendsEncodingParameter.class);
    Assert.assertEquals(Charset.defaultCharset(), params.getEncoding());
  }

  @Test
  public void testSetEncodingParameter() {
    Collection<Charset> availableCharset = Charset.availableCharsets().values();
    String notTheDefaultCharset = "UTF-8";
    for (Charset charset : availableCharset) {
      if (!charset.equals(Charset.defaultCharset())) {
        notTheDefaultCharset = charset.name();
        break;
      }
    }

    String args[] = ("-something aValue -encoding " + notTheDefaultCharset).split(" ");
    Assert.assertTrue(ArgumentParser.validateArguments(args, ExtendsEncodingParameter.class));

    ExtendsEncodingParameter params = ArgumentParser.parse(args, ExtendsEncodingParameter.class);
    Assert.assertEquals(Charset.forName(notTheDefaultCharset), params.getEncoding());
  }
}
