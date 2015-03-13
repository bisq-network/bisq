/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.util;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.*;

public class RepeatRuleTests {

    private static final int EXPECTED_COUNT = 10;
    private static int ACTUAL_BEFORE_COUNT;
    private static int ACTUAL_TEST_COUNT;
    private static int ACTUAL_AFTER_COUNT;

    public @Rule RepeatRule repeatRule = new RepeatRule();

    @BeforeClass
    public static void beforeTests() {
        ACTUAL_BEFORE_COUNT = 0;
        ACTUAL_TEST_COUNT = 0;
        ACTUAL_AFTER_COUNT = 0;
    }

    @Before
    public void setUp() {
        ACTUAL_BEFORE_COUNT++;
    }

    @Test
    @Repeat(EXPECTED_COUNT)
    public void shouldBeRepeated() {
        ACTUAL_TEST_COUNT++;
    }

    @After
    public void tearDown() {
        ACTUAL_AFTER_COUNT++;
    }

    @AfterClass
    public static void afterTests() {
        assertThat(ACTUAL_BEFORE_COUNT, equalTo(EXPECTED_COUNT));
        assertThat(ACTUAL_TEST_COUNT, equalTo(EXPECTED_COUNT));
        assertThat(ACTUAL_AFTER_COUNT, equalTo(EXPECTED_COUNT));
    }
}
