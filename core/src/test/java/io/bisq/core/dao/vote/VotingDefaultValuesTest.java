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

package io.bisq.core.dao.vote;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;


public class VotingDefaultValuesTest {
    private static final Logger log = LoggerFactory.getLogger(VotingDefaultValuesTest.class);

    @Test
    public void testValidTxs() {
        VotingDefaultValues votingDefaultValues = new VotingDefaultValues();

        assertEquals(10, votingDefaultValues.getAdjustedValue(100, 0));
        assertEquals(100, votingDefaultValues.getAdjustedValue(100, 127));
        assertEquals(1000, votingDefaultValues.getAdjustedValue(100, 254));
    }
} 