/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  under the License.
 */

package opennlp.tools.formats;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;

import org.junit.Test;

import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.StringList;

public class NameFinderCensus90NameStreamTest {

  private static ObjectStream<StringList> openData(String name)
      throws IOException {
    InputStreamFactory in = new ResourceAsStreamFactory(
        NameFinderCensus90NameStreamTest.class,
        "/opennlp/tools/formats/" + name);

    return new NameFinderCensus90NameStream(in, UTF_8);
  }

  @Test
  public void testParsingEnglishSample() throws IOException {

    ObjectStream<StringList> sampleStream = openData("census90.sample");

    StringList personName = sampleStream.read();

    // verify the first 5 taken from the Surname data
    assertNotNull(personName);
    assertEquals("Smith", personName.getToken(0));
    personName = sampleStream.read();
    assertNotNull(personName);
    assertEquals("Johnson", personName.getToken(0));
    personName = sampleStream.read();
    assertNotNull(personName);
    assertEquals("Williams", personName.getToken(0));
    personName = sampleStream.read();
    assertNotNull(personName);
    assertEquals("Jones", personName.getToken(0));
    personName = sampleStream.read();
    assertNotNull(personName);
    assertEquals("Brown", personName.getToken(0));

    // verify the next 5 taken from the female names
    personName = sampleStream.read();
    assertNotNull(personName);
    assertEquals("Mary", personName.getToken(0));
    personName = sampleStream.read();
    assertNotNull(personName);
    assertEquals("Patricia", personName.getToken(0));
    personName = sampleStream.read();
    assertNotNull(personName);
    assertEquals("Linda", personName.getToken(0));
    personName = sampleStream.read();
    assertNotNull(personName);
    assertEquals("Barbara", personName.getToken(0));
    personName = sampleStream.read();
    assertNotNull(personName);
    assertEquals("Elizabeth", personName.getToken(0));

    // verify the last 5 taken from the male names
    personName = sampleStream.read();
    assertNotNull(personName);
    assertEquals("James", personName.getToken(0));
    personName = sampleStream.read();
    assertNotNull(personName);
    assertEquals("John", personName.getToken(0));
    personName = sampleStream.read();
    assertNotNull(personName);
    assertEquals("Robert", personName.getToken(0));
    personName = sampleStream.read();
    assertNotNull(personName);
    assertEquals("Michael", personName.getToken(0));
    personName = sampleStream.read();
    assertNotNull(personName);
    assertEquals("William", personName.getToken(0));

    // verify the end of the file.
    personName = sampleStream.read();
    assertNull(personName);
  }

}