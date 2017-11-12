/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.common.app;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CapabilitiesTest {

    @Test
    public void testVersionNumber() {
        // if required are null or empty its true
        assertTrue(Capabilities.isCapabilitySupported(null, null));
        assertTrue(Capabilities.isCapabilitySupported(null, Arrays.asList()));
        assertTrue(Capabilities.isCapabilitySupported(null, Arrays.asList(0)));
        assertTrue(Capabilities.isCapabilitySupported(Arrays.asList(), null));
        assertTrue(Capabilities.isCapabilitySupported(Arrays.asList(), Arrays.asList()));
        assertTrue(Capabilities.isCapabilitySupported(Arrays.asList(), Arrays.asList(0)));

        // required are not null and not empty but supported is null or empty its false
        assertFalse(Capabilities.isCapabilitySupported(Arrays.asList(0), null));
        assertFalse(Capabilities.isCapabilitySupported(Arrays.asList(0), Arrays.asList()));

        // single match
        assertTrue(Capabilities.isCapabilitySupported(Arrays.asList(0), Arrays.asList(0)));
        assertFalse(Capabilities.isCapabilitySupported(Arrays.asList(1), Arrays.asList(0)));
        assertFalse(Capabilities.isCapabilitySupported(Arrays.asList(0), Arrays.asList(1)));

        // multi match
        assertTrue(Capabilities.isCapabilitySupported(Arrays.asList(0), Arrays.asList(0, 1)));
        assertFalse(Capabilities.isCapabilitySupported(Arrays.asList(0), Arrays.asList(1, 2)));
        assertTrue(Capabilities.isCapabilitySupported(Arrays.asList(0, 1), Arrays.asList(0, 1)));
        assertTrue(Capabilities.isCapabilitySupported(Arrays.asList(0, 1), Arrays.asList(1,0)));
        assertFalse(Capabilities.isCapabilitySupported(Arrays.asList(0, 1), Arrays.asList(0)));
    }
}
