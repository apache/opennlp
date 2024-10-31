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

package opennlp.tools.cmdline.namefind.generator;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

abstract class AbstractNewsGenerator {

  protected Calendar cal = new GregorianCalendar();

  abstract String[] getSupportedDateFormats();

  Date generateRandomDate(Calendar cal) {
    cal.set(1900, Calendar.JANUARY, 1);
    long startMillis = cal.getTimeInMillis();
    long endMillis = new Date().getTime();
    long randomMillisSinceEpoch = ThreadLocalRandom.current().nextLong(startMillis, endMillis);
    return new Date(randomMillisSinceEpoch);
  }

  String formatDateWithTags(Date date, Locale loc) {
    String[] formats = getSupportedDateFormats();
    SimpleDateFormat dateFormat = new SimpleDateFormat(formats[new Random().nextInt(formats.length)], loc);
    return dateFormat.format(date);
  }
}
