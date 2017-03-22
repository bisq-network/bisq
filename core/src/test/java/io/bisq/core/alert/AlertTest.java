/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.alert;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AlertTest {
    private static final Logger log = LoggerFactory.getLogger(AlertTest.class);

    @Test
    public void testIsNewVersion() {
        Alert alert = new Alert(null, true, "0.4.9.9.1");
        assertTrue(alert.isNewVersion("0.4.9.9"));
        assertTrue(alert.isNewVersion("0.4.9.8"));
        assertTrue(alert.isNewVersion("0.4.9"));
        assertTrue(alert.isNewVersion("0.4.9.9.0"));
        assertFalse(alert.isNewVersion("0.4.9.9.1"));

        alert = new Alert(null, true, "0.4.9.9.2");
        assertTrue(alert.isNewVersion("0.4.9.9.1"));
        assertFalse(alert.isNewVersion("0.4.9.9.2"));
        assertTrue(alert.isNewVersion("0.4.9.8"));
        assertTrue(alert.isNewVersion("0.4.9"));

        alert = new Alert(null, true, "0.4.9.9");
        assertTrue(alert.isNewVersion("0.4.9"));
        assertTrue(alert.isNewVersion("0.4.9.8"));
        assertFalse(alert.isNewVersion("0.4.9.9"));
    }
}
