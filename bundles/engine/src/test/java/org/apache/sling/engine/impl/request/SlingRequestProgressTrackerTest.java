/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.engine.impl.request;

import static org.junit.Assert.assertEquals;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;

import org.junit.Test;

public class SlingRequestProgressTrackerTest {

    @Test
    public void messageFormatting() {
        final SlingRequestProgressTracker tracker = new SlingRequestProgressTracker();
        tracker.startTimer("foo");
        tracker.log("one {0}, two {1}, three {2}", "eins", "zwei", "drei");
        tracker.startTimer("bar");
        tracker.logTimer("bar");
        tracker.logTimer("foo");
        tracker.done();

        final String[] expected = {
                "TIMER_START{Request Processing}\n",
                "COMMENT timer_end format is {<elapsed msec>,<timer name>} <optional message>\n",
                "TIMER_START{foo}\n",
                "LOG one eins, two zwei, three drei\n",
                "TIMER_START{bar}\n",
                "TIMER_END{?,bar}\n",
                "TIMER_END{?,foo}\n",
                "TIMER_END{?,Request Processing} Request Processing\n"
        };

        final Iterator<String> messages = tracker.getMessages();
        int messageCounter = 0;
        while (messages.hasNext()) {
            final String m = messages.next();
            final String e = expected[messageCounter++];
            if (e.startsWith("TIMER_END{")) {
                // account for the counter in the string
                assertEquals(substringAfter(e, ','), substringAfter(m, ','));
            } else {
                // strip off counter
                assertEquals(e, m.substring(8));
            }
        }

        assertEquals(expected.length, messageCounter);
    }

    private String substringAfter(String string, char ch) {
        final int pos = string.indexOf(ch);
        return string.substring(pos);
    }

    @Test
    public void testFastFormat() {
        FormatTest[] tests = {
                // Data types
                new FormatTest("{0}", 1),
                new FormatTest("{0}", Integer.MAX_VALUE),
                new FormatTest("{0}", Long.MAX_VALUE),
                new FormatTest("{0}", Math.PI),
                new FormatTest("{0}", 123456.123456),
                new FormatTest("{0}", Float.MAX_VALUE),
                new FormatTest("{0}", new Date()),
                new FormatTest("{0}", Calendar.getInstance()),
                new FormatTest("{0}", true),
                new FormatTest("{0}", false),
                new FormatTest("{0}", new int[] {1,2,3}),
                new FormatTest("{0}", (Object) new Integer[] {1,2,3}),
                new FormatTest("{0}", Arrays.asList(1,2,3)),
                new FormatTest("{0}", "text"),

                // Patterns
                new FormatTest("{0}{0}", 1, 2),
                new FormatTest("a{0}b{1}c", 1, 2),
                new FormatTest("a{0}b{1}c{2}d", 1, 2, 3),
                new FormatTest("a{0}bb{1}ccc{2}dddd", 10, 20, 30),
                new FormatTest("c{1}b{0}a", 1, 2),
                new FormatTest("c{1}b{0}a{1}c", 1, 2),

                // Type/style
                new FormatTest("c{0,number,#.##}b{0}a{1}c", 1, 2),
                new FormatTest("{0,number,#.##}", 1.2345),

                // Escaping
                new FormatTest("'{0}'", 1)
        };

        SlingRequestProgressTracker progressTracker = new SlingRequestProgressTracker();
        for (FormatTest test : tests) {
            String expected = MessageFormat.format(test.pattern, test.args);
            String actual = progressTracker.fastFormat(test.pattern, test.args);
            assertEquals(expected, actual);
        }
    }

    private static class FormatTest {
        String pattern;
        Object[] args;
        public FormatTest(String pattern, Object... args) {
            this.pattern = pattern;
            this.args = args;
        }
    }

}
