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

import io.bisq.common.app.Version;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.Assert.*;

@SuppressWarnings("UnusedAssignment")
public class VoteManagerTest {
    private static final Logger log = LoggerFactory.getLogger(VoteManagerTest.class);

    @Test
    public void testGetVoteItemListFromOpReturnData() {
        VotingManager votingManager = new VotingManager(new VotingDefaultValues());
        byte[] opReturnData;
        VoteItemsList voteItemsList;

        // wrong version -> no votes
        opReturnData = new byte[]{(byte) 0x00};
        voteItemsList = votingManager.getVoteItemListFromOpReturnData(opReturnData);
        assertFalse(voteItemsList.hasVotedOnAnyItem());

        // right version -> no compensation requests
        opReturnData = new byte[22];
        opReturnData[0] = Version.VOTING_VERSION;
        opReturnData[21] = (byte) 0;
        voteItemsList = votingManager.getVoteItemListFromOpReturnData(opReturnData);
        assertFalse(voteItemsList.hasVotedOnAnyItem());

        // right version -> wrong size (need to be 0 or multiple of 2)
        opReturnData = new byte[22];
        opReturnData[0] = Version.VOTING_VERSION;
        opReturnData[21] = (byte) 1;
        try {
            votingManager.getVoteItemListFromOpReturnData(opReturnData);
            fail("Expected an IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), startsWith(VotingManager.ERROR_MSG_WRONG_SIZE));
        }

        // compensation requests set but no votes
        opReturnData = new byte[24];
        opReturnData[0] = Version.VOTING_VERSION;
        opReturnData[21] = (byte) 2;
        opReturnData[22] = (byte) 0x00;
        opReturnData[23] = (byte) 0x00;
        voteItemsList = votingManager.getVoteItemListFromOpReturnData(opReturnData);
        assertFalse(voteItemsList.hasVotedOnAnyItem());

        // has voted all false, but we found a accepted flag
        opReturnData = new byte[24];
        opReturnData[0] = Version.VOTING_VERSION;
        opReturnData[21] = (byte) 2;
        opReturnData[22] = (byte) 0;
        opReturnData[23] = (byte) 1;
        try {
            votingManager.getVoteItemListFromOpReturnData(opReturnData);
            fail("Expected an IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is(VotingManager.ERROR_MSG_INVALID_COMP_REQ_MAPS));
        }

        // has voted on item 2, but we found a accepted flag at item 1
        opReturnData = new byte[24];
        opReturnData[0] = Version.VOTING_VERSION;
        opReturnData[21] = (byte) 2;
        opReturnData[22] = (byte) 2;
        opReturnData[23] = (byte) 1;
        try {
            votingManager.getVoteItemListFromOpReturnData(opReturnData);
            fail("Expected an IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is(VotingManager.ERROR_MSG_INVALID_COMP_REQ_VAL));
        }

        // has voted on all and are declined
        opReturnData = new byte[24];
        opReturnData[0] = Version.VOTING_VERSION;
        opReturnData[21] = (byte) 2;
        opReturnData[22] = (byte) 255;
        opReturnData[23] = (byte) 0;
        voteItemsList = votingManager.getVoteItemListFromOpReturnData(opReturnData);
        assertTrue(voteItemsList.hasVotedOnAnyItem());

        // has voted on all and all accepted
        opReturnData = new byte[24];
        opReturnData[0] = Version.VOTING_VERSION;
        opReturnData[21] = (byte) 2;
        opReturnData[22] = (byte) 255;
        opReturnData[23] = (byte) 255;
        voteItemsList = votingManager.getVoteItemListFromOpReturnData(opReturnData);
        assertTrue(voteItemsList.hasVotedOnAnyItem());

        // wrong length
        opReturnData = new byte[23];
        opReturnData[0] = Version.VOTING_VERSION;
        opReturnData[21] = (byte) 0;
        opReturnData[22] = VotingType.MAKER_FEE_IN_BTC.code;
        try {
            votingManager.getVoteItemListFromOpReturnData(opReturnData);
            fail("Expected an IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is(VotingManager.ERROR_MSG_MISSING_BYTE));
        }

        // wrong length
        opReturnData = new byte[25];
        opReturnData[0] = Version.VOTING_VERSION;
        opReturnData[21] = (byte) 2;
        opReturnData[22] = (byte) 255;
        opReturnData[23] = (byte) 255;
        opReturnData[24] = VotingType.MAKER_FEE_IN_BTC.code;
        try {
            votingManager.getVoteItemListFromOpReturnData(opReturnData);
            fail("Expected an IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is(VotingManager.ERROR_MSG_MISSING_BYTE));
        }

        // Invalid param vote 255 is not allowed only 0-254
        opReturnData = new byte[24];
        opReturnData[0] = Version.VOTING_VERSION;
        opReturnData[21] = (byte) 0;
        opReturnData[22] = VotingType.MAKER_FEE_IN_BTC.code;
        opReturnData[23] = (byte) 255;
        try {
            votingManager.getVoteItemListFromOpReturnData(opReturnData);
            fail("Expected an IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), startsWith(VotingDefaultValues.ERROR_MSG_INVALID_VALUE));
        }

        // Valid param vote 
        opReturnData = new byte[24];
        opReturnData[0] = Version.VOTING_VERSION;
        opReturnData[21] = (byte) 0;
        opReturnData[22] = VotingType.MAKER_FEE_IN_BTC.code;
        opReturnData[23] = (byte) 254;
        voteItemsList = votingManager.getVoteItemListFromOpReturnData(opReturnData);
        assertTrue(voteItemsList.hasVotedOnAnyItem());
        assertEquals(opReturnData[23], voteItemsList.getVoteItemByVotingType(VotingType.MAKER_FEE_IN_BTC).get().getValue());

        // Valid param votes
        opReturnData = new byte[26];
        opReturnData[0] = Version.VOTING_VERSION;
        opReturnData[21] = (byte) 0;
        opReturnData[22] = VotingType.MAKER_FEE_IN_BTC.code;
        opReturnData[23] = (byte) 130;
        opReturnData[24] = VotingType.TAKER_FEE_IN_BTC.code;
        opReturnData[25] = (byte) 120;
        voteItemsList = votingManager.getVoteItemListFromOpReturnData(opReturnData);
        assertTrue(voteItemsList.hasVotedOnAnyItem());
        assertEquals(opReturnData[23], voteItemsList.getVoteItemByVotingType(VotingType.MAKER_FEE_IN_BTC).get().getValue());
        assertEquals(opReturnData[25], voteItemsList.getVoteItemByVotingType(VotingType.TAKER_FEE_IN_BTC).get().getValue());
    }


    @Test
    public void testCalculateHash() {
        VotingManager votingManager = new VotingManager(new VotingDefaultValues());

        // assertEquals(10, votingManager.calculateHash(100, 0));
    }

} 