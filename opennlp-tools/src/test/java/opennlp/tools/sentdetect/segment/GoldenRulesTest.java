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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Thanks for the GoldenRules of
 * <a href="https://github.com/diasks2/pragmatic_segmenter">pragmatic_segmenter</a>
 */
public class GoldenRulesTest {

  public Cleaner cleaner = new Cleaner();

  public List<String> segment(String text) {
    if (cleaner != null) {
      text = cleaner.clean(text);
    }
    LanguageRule languageRule = new EnglishRule().getLanguageRule();
    Map<String, Object> paramMap = new HashMap<>();
    paramMap.put(LanguageTool.MAX_LOOKBEHIND_LENGTH_PARAM, 50);
    LanguageTool languageTool = new LanguageTool("eng", languageRule, paramMap);
    SentenceTokenizer sentenceTokenizer = new SentenceTokenizer(languageTool, text);

    return sentenceTokenizer.sentenceTokenizer();
  }

  @Test
  public void test1() {
    List<String> segment = segment("Hello World. My name is Jonas.");
    System.out.println(segment);
    Assert.assertEquals(2, segment.size());
    Assert.assertEquals("[Hello World., My name is Jonas.]", segment.toString());
  }

  @Test
  public void test2() {
    List<String> segment = segment("What is your name? My name is Jonas.");
    System.out.println(segment);
    Assert.assertEquals(2, segment.size());
    Assert.assertEquals("[What is your name?, My name is Jonas.]", segment.toString());
  }

  @Test
  public void test3() {
    List<String> segment = segment("There it is! I found it.");
    System.out.println(segment);
    Assert.assertEquals(2, segment.size());
    Assert.assertEquals("[There it is!, I found it.]", segment.toString());
  }

  @Test
  public void test4() {
    List<String> segment = segment("My name is Jonas E. Smith.");
    System.out.println(segment);
    Assert.assertEquals(1, segment.size());
    Assert.assertEquals("[My name is Jonas E. Smith.]", segment.toString());
  }

  @Test
  public void test5() {
    List<String> segment = segment("Please turn to p. 55.");
    System.out.println(segment);
    Assert.assertEquals(1, segment.size());
    Assert.assertEquals("[Please turn to p. 55.]", segment.toString());
  }

  @Test
  public void test6() {
    List<String> segment = segment("Were Jane and co. at the party?");
    System.out.println(segment);
    Assert.assertEquals(1, segment.size());
    Assert.assertEquals("[Were Jane and co. at the party?]", segment.toString());
  }

  @Test
  public void test7() {
    List<String> segment = segment("They closed the deal with Pitt, Briggs & Co. at noon.");
    System.out.println(segment);
    Assert.assertEquals(1, segment.size());
    Assert.assertEquals("[They closed the deal with Pitt, Briggs & Co. at noon.]",
        segment.toString());
  }

  @Test
  public void test8() {
    List<String> segment = segment("Let's ask Jane and co. They should know.");
    System.out.println(segment);
    Assert.assertEquals(2, segment.size());
    Assert.assertEquals("[Let's ask Jane and co., They should know.]", segment.toString());
  }

  @Test
  public void test9() {
    List<String> segment = segment("They closed the deal with Pitt, Briggs & Co. It closed yesterday.");
    System.out.println(segment);
    Assert.assertEquals(2, segment.size());
    Assert.assertEquals("[They closed the deal with Pitt, Briggs & Co., It closed yesterday.]",
        segment.toString());
  }

  @Test
  public void test10() {
    List<String> segment = segment("I can see Mt. Fuji from here.");
    System.out.println(segment);
    Assert.assertEquals(1, segment.size());
    Assert.assertEquals("[I can see Mt. Fuji from here.]", segment.toString());
  }

  @Test
  public void test11() {
    List<String> segment = segment("St. Michael's Church is on 5th st. near the light.");
    System.out.println(segment);
    Assert.assertEquals(1, segment.size());
    Assert.assertEquals("[St. Michael's Church is on 5th st. near the light.]",
        segment.toString());
  }

  @Test
  public void test12() {
    List<String> segment = segment("That is JFK Jr.'s book.");
    System.out.println(segment);
    Assert.assertEquals(1, segment.size());
    Assert.assertEquals("[That is JFK Jr.'s book.]", segment.toString());
  }

  @Test
  public void test13() {
    List<String> segment = segment("I visited the U.S.A. last year.");
    System.out.println(segment);
    Assert.assertEquals(1, segment.size());
    Assert.assertEquals("[I visited the U.S.A. last year.]", segment.toString());
  }

  @Test
  public void test14() {
    List<String> segment = segment("I live in the E.U. How about you?");
    System.out.println(segment);
    Assert.assertEquals(2, segment.size());
    Assert.assertEquals("[I live in the E.U., How about you?]", segment.toString());
  }

  @Test
  public void test15() {
    List<String> segment = segment("I live in the U.S. How about you?");
    System.out.println(segment);
    Assert.assertEquals(2, segment.size());
    Assert.assertEquals("[I live in the U.S., How about you?]", segment.toString());
  }

  @Test
  public void test16() {
    List<String> segment = segment("I work for the U.S. Government in Virginia.");
    System.out.println(segment);
    Assert.assertEquals(1, segment.size());
    Assert.assertEquals("[I work for the U.S. Government in Virginia.]", segment.toString());
  }

  @Test
  public void test17() {
    List<String> segment = segment("I have lived in the U.S. for 20 years.");
    System.out.println(segment);
    Assert.assertEquals(1, segment.size());
    Assert.assertEquals("[I have lived in the U.S. for 20 years.]", segment.toString());
  }

  @Test
  public void test18() {
    List<String> segment = segment("At 5 a.m. Mr. Smith went to the bank. He left the" +
        " bank at 6 P.M. Mr. Smith then went to the store.");
    System.out.println(segment);
    Assert.assertEquals(3, segment.size());
    Assert.assertEquals("[At 5 a.m. Mr. Smith went to the bank., He left the bank at" +
        " 6 P.M., Mr. Smith then went to the store.]", segment.toString());
  }

  @Test
  public void test19() {
    List<String> segment = segment("She has $100.00 in her bag.");
    System.out.println(segment);
    Assert.assertEquals(1, segment.size());
    Assert.assertEquals("[She has $100.00 in her bag.]", segment.toString());
  }

  @Test
  public void test20() {
    List<String> segment = segment("She has $100.00. It is in her bag.");
    System.out.println(segment);
    Assert.assertEquals(2, segment.size());
    Assert.assertEquals("[She has $100.00., It is in her bag.]", segment.toString());
  }

  @Test
  public void test21() {
    List<String> segment = segment("He teaches science (He previously worked for" +
        " 5 years as an engineer.) at the local University.");
    System.out.println(segment);
    Assert.assertEquals(1, segment.size());
    Assert.assertEquals("[He teaches science (He previously worked for 5 years" +
        " as an engineer.) at the local University.]", segment.toString());
  }

  @Test
  public void test22() {
    List<String> segment = segment("Her email is Jane.Doe@example.com. I sent her an email.");
    System.out.println(segment);
    Assert.assertEquals(2, segment.size());
    Assert.assertEquals("[Her email is Jane.Doe@example.com., I sent her an email.]",
        segment.toString());
  }

  @Test
  public void test23() {
    List<String> segment = segment("The site is: https://www.example.50.com/new-site/" +
        "awesome_content.html. Please check it out.");
    System.out.println(segment);
    Assert.assertEquals(2, segment.size());
    Assert.assertEquals("[The site is: https://www.example.50.com/new-site/" +
        "awesome_content.html., Please check it out.]", segment.toString());
  }

  @Test
  public void test24() {
    List<String> segment = segment("She turned to him, 'This is great.' she said.");
    System.out.println(segment);
    Assert.assertEquals(1, segment.size());
    Assert.assertEquals("[She turned to him, 'This is great.' she said.]",
        segment.toString());
  }

  @Test
  public void test25() {
    List<String> segment = segment("She turned to him, \"This is great.\" she said.");
    System.out.println(segment);
    Assert.assertEquals(1, segment.size());
    Assert.assertEquals("[She turned to him, \"This is great.\" she said.]",
        segment.toString());
  }

  @Test
  public void test26() {
    List<String> segment = segment("She turned to him, \"This is great.\"" +
        " She held the book out to show him.");
    System.out.println(segment);
    Assert.assertEquals(2, segment.size());
    Assert.assertEquals("[She turned to him, \"This is great.\", " +
        "She held the book out to show him.]", segment.toString());
  }

  @Test
  public void test27() {
    List<String> segment = segment("Hello!! Long time no see.");
    System.out.println(segment);
    Assert.assertEquals(2, segment.size());
    Assert.assertEquals("[Hello!!, Long time no see.]", segment.toString());
  }

  @Test
  public void test28() {
    List<String> segment = segment("Hello?? Who is there?");
    System.out.println(segment);
    Assert.assertEquals(2, segment.size());
    Assert.assertEquals("[Hello??, Who is there?]", segment.toString());
  }

  @Test
  public void test29() {
    List<String> segment = segment("Hello!? Is that you?");
    System.out.println(segment);
    Assert.assertEquals(2, segment.size());
    Assert.assertEquals("[Hello!?, Is that you?]", segment.toString());
  }

  @Test
  public void test30() {
    List<String> segment = segment("Hello?! Is that you?");
    System.out.println(segment);
    Assert.assertEquals(2, segment.size());
    Assert.assertEquals("[Hello?!, Is that you?]", segment.toString());
  }

  @Test
  public void test31() {
    List<String> segment = segment("1.) The first item 2.) The second item");
    System.out.println(segment);
    Assert.assertEquals(2, segment.size());
    Assert.assertEquals("[1.) The first item, 2.) The second item]", segment.toString());
  }

  @Test
  public void test32() {
    List<String> segment = segment("1.) The first item. 2.) The second item.");
    System.out.println(segment);
    Assert.assertEquals(2, segment.size());
    Assert.assertEquals("[1.) The first item., 2.) The second item.]", segment.toString());
  }

  @Test
  public void test33() {
    List<String> segment = segment("1) The first item 2) The second item");
    System.out.println(segment);
    Assert.assertEquals(2, segment.size());
    Assert.assertEquals("[1) The first item, 2) The second item]", segment.toString());
  }

  @Test
  public void test34() {
    List<String> segment = segment("1) The first item. 2) The second item.");
    System.out.println(segment);
    Assert.assertEquals(2, segment.size());
    Assert.assertEquals("[1) The first item., 2) The second item.]", segment.toString());
  }

  @Test
  public void test35() {
    List<String> segment = segment("1. The first item 2. The second item");
    System.out.println(segment);
    Assert.assertEquals(2, segment.size());
    Assert.assertEquals("[1. The first item, 2. The second item]", segment.toString());
  }

  @Test
  public void test36() {
    List<String> segment = segment("1. The first item. 2. The second item.");
    System.out.println(segment);
    Assert.assertEquals(2, segment.size());
    Assert.assertEquals("[1. The first item., 2. The second item.]", segment.toString());
  }

  @Test
  public void test37() {
    List<String> segment = segment("• 9. The first item • 10. The second item");
    System.out.println(segment);
    Assert.assertEquals(2, segment.size());
    Assert.assertEquals("[• 9. The first item, • 10. The second item]", segment.toString());
  }

  @Test
  public void test38() {
    List<String> segment = segment("⁃9. The first item ⁃10. The second item");
    System.out.println(segment);
    Assert.assertEquals(2, segment.size());
    Assert.assertEquals("[⁃9. The first item, ⁃10. The second item]", segment.toString());
  }

  @Ignore
  @Test
  public void test39() {
    List<String> segment = segment("a. The first item b. The second item c. The third list item");
    System.out.println(segment);
    Assert.assertEquals(3, segment.size());
    Assert.assertEquals("[a. The first item, b. The second item," +
        " c. The third list item]", segment.toString());
  }

  @Test
  public void test40() {
    cleaner.pdf();
    List<String> segment = segment("This is a sentence\ncut off in the middle because pdf.");
    System.out.println(segment);
    Assert.assertEquals(1, segment.size());
    Assert.assertEquals("[This is a sentence cut off in the middle because pdf.]",
        segment.toString());
  }

  @Test
  public void test41() {
    cleaner.pdf();
    List<String> segment = segment("It was a cold \nnight in the city.");
    System.out.println(segment);
    Assert.assertEquals(1, segment.size());
    Assert.assertEquals("[It was a cold night in the city.]", segment.toString());
  }

  @Test
  public void test42() {
    cleaner.rules();
    List<String> segment = segment("features\ncontact manager\nevents, activities\n");
    System.out.println(segment);
    Assert.assertEquals(3, segment.size());
    Assert.assertEquals("[features, contact manager, events, activities]",
        segment.toString());
  }

  @Test
  public void test43() {
    List<String> segment = segment("You can find it at N°. 1026.253.553." +
        " That is where the treasure is.");
    System.out.println(segment);
    Assert.assertEquals(2, segment.size());
    Assert.assertEquals("[You can find it at N°. 1026.253.553.," +
        " That is where the treasure is.]", segment.toString());
  }

  @Test
  public void test44() {
    List<String> segment = segment("She works at Yahoo! in the accounting department.");
    System.out.println(segment);
    Assert.assertEquals(1, segment.size());
    Assert.assertEquals("[She works at Yahoo! in the accounting department.]",
        segment.toString());
  }

  @Test
  public void test45() {
    List<String> segment = segment("We make a good team, you and I." +
        " Did you see Albert I. Jones yesterday?");
    System.out.println(segment);
    Assert.assertEquals(2, segment.size());
    Assert.assertEquals("[We make a good team, you and I., Did you see Albert" +
        " I. Jones yesterday?]", segment.toString());
  }

  @Test
  public void test46() {
    List<String> segment = segment("Thoreau argues that by simplifying one’s life," +
        " “the laws of the universe will appear less complex. . . .”");
    System.out.println(segment);
    Assert.assertEquals(1, segment.size());
    Assert.assertEquals("[Thoreau argues that by simplifying one’s life, " +
        "“the laws of the universe will appear less complex. . . .”]", segment.toString());
  }

  @Test
  public void test47() {
    List<String> segment = segment("\"Bohr [...] used the analogy of parallel" +
        " stairways [...]\" (Smith 55).");
    System.out.println(segment);
    Assert.assertEquals(1, segment.size());
    Assert.assertEquals("[\"Bohr [...] used the analogy of parallel stairways" +
        " [...]\" (Smith 55).]", segment.toString());
  }

  @Test
  public void test48() {
    List<String> segment = segment("If words are left off at the end of a sentence," +
        " and that is all that is omitted, indicate the omission with ellipsis marks " +
        "(preceded and followed by a space) and then indicate the end of the sentence " +
        "with a period . . . . Next sentence.");
    System.out.println(segment);
    Assert.assertEquals(2, segment.size());
    Assert.assertEquals("[If words are left off at the end of a sentence, " +
        "and that is all that is omitted, indicate the omission with ellipsis marks " +
        "(preceded and followed by a space) and then indicate the end of the sentence " +
        "with a period . . . ., Next sentence.]", segment.toString());
  }

  @Test
  public void test49() {
    List<String> segment = segment("I never meant that.... She left the store.");
    System.out.println(segment);
    Assert.assertEquals(2, segment.size());
    Assert.assertEquals("[I never meant that...., She left the store.]", segment.toString());
  }

  @Ignore
  @Test
  public void test50() {
    List<String> segment = segment("I wasn’t really ... well, what I mean...see . . . " +
        "what I'm saying, the thing is . . . I didn’t mean it.");
    System.out.println(segment);
    Assert.assertEquals(1, segment.size());
    Assert.assertEquals("[I wasn’t really ... well, what I mean...see . . . what " +
        "I'm saying, the thing is . . . I didn’t mean it.", segment.toString());
  }

  @Ignore
  @Test
  public void test51() {
    List<String> segment = segment("One further habit which was somewhat weakened " +
        ". . . was that of combining words into self-interpreting compounds. . . . " +
        "The practice was not abandoned. . . .");
    System.out.println(segment);
    Assert.assertEquals(2, segment.size());
    Assert.assertEquals("[One further habit which was somewhat weakened . . . " +
        "was that of combining words into self-interpreting compounds., . . . " +
        "The practice was not abandoned. . . .]", segment.toString());
  }

  @Ignore
  @Test
  public void test52() {
    List<String> segment = segment("Hello world.Today is Tuesday.Mr. Smith went to " +
        "the store and bought 1,000.That is a lot.");
    System.out.println(segment);
    Assert.assertEquals(4, segment.size());
    Assert.assertEquals("[Hello world., Today is Tuesday., Mr. Smith went to the" +
        " store and bought 1,000., That is a lot.]", segment.toString());
  }
}
