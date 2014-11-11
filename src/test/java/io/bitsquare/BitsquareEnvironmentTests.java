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

package io.bitsquare;

import io.bitsquare.app.BitsquareEnvironment;

import org.junit.Test;

import joptsimple.OptionParser;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.*;

public class BitsquareEnvironmentTests {

    @Test
    public void test() {
        String[] args = new String[]{ "--arg1=val1", "--arg2=val2" };
        OptionParser parser = new OptionParser();
        parser.accepts("arg1").withRequiredArg();
        parser.accepts("arg2").withRequiredArg();
        BitsquareEnvironment env = new BitsquareEnvironment(parser.parse(args));
        assertThat(env.getProperty("arg1"), equalTo("val1"));
        assertThat(env.getProperty("arg2"), equalTo("val2"));
    }
}