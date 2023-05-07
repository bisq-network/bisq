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

package bisq.core.dao.voting.voteresult;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static bisq.core.dao.governance.merit.MeritConsensus.getWeightedMeritAmount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
public class VoteResultConsensusTest {
    @BeforeEach
    public void setup() {
    }

    @Test
    public void testGetWeightedMeritAmount() {
        int currentChainHeight;
        int blocksPerYear = 50_000; // 144*365=51264;
        currentChainHeight = 1_000_000;

        assertEquals(100000, getWeightedMeritAmount(100_000, 1_000_000,
                currentChainHeight, blocksPerYear), "fresh issuance");
        assertEquals(75000, getWeightedMeritAmount(100_000, 975_000,
                currentChainHeight, blocksPerYear), "0.5 year old issuance");
        assertEquals(50000, getWeightedMeritAmount(100_000, 950_000,
                currentChainHeight, blocksPerYear), "1 year old issuance");
        assertEquals(25000, getWeightedMeritAmount(100_000, 925_000,
                currentChainHeight, blocksPerYear), "1.5 year old issuance");
        assertEquals(0, getWeightedMeritAmount(100_000, 900_000,
                currentChainHeight, blocksPerYear), "2 year old issuance");
        assertEquals(0, getWeightedMeritAmount(100_000, 850_000,
                currentChainHeight, blocksPerYear), "3 year old issuance");


        assertEquals(99999, getWeightedMeritAmount(100_000, 999_999,
                currentChainHeight, blocksPerYear), "1 block old issuance");
        assertEquals(99998, getWeightedMeritAmount(100_000, 999_998,
                currentChainHeight, blocksPerYear), "2 block old issuance");
        assertEquals(99990, getWeightedMeritAmount(100_000, 999_990,
                currentChainHeight, blocksPerYear), "10 blocks old issuance");
        assertEquals(99900, getWeightedMeritAmount(100_000, 999_900,
                currentChainHeight, blocksPerYear), "100 blocks old issuance");
        assertEquals(1, getWeightedMeritAmount(100_000, 900_001,
                currentChainHeight, blocksPerYear), "99_999 blocks old issuance");
        assertEquals(10, getWeightedMeritAmount(100_000, 900_010,
                currentChainHeight, blocksPerYear), "99_990 blocks old issuance");
        assertEquals(0, getWeightedMeritAmount(100_000, 899_999,
                currentChainHeight, blocksPerYear), "100_001 blocks old issuance");
        assertEquals(0, getWeightedMeritAmount(100_000, 0,
                currentChainHeight, blocksPerYear), "1_000_000 blocks old issuance");
    }

    @Test
    public void testInvalidChainHeight() {
        assertThrows(IllegalArgumentException.class, () -> getWeightedMeritAmount(100_000, 2_000_000, 1_000_000, 1_000_000));
    }

    @Test
    public void testInvalidIssuanceHeight() {
        assertThrows(IllegalArgumentException.class, () -> getWeightedMeritAmount(100_000, -1, 1_000_000, 1_000_000));
    }

    @Test
    public void testInvalidAmount() {
        assertThrows(IllegalArgumentException.class, () -> getWeightedMeritAmount(-100_000, 1, 1_000_000, 1_000_000));
    }

    @Test
    public void testInvalidCurrentChainHeight() {
        assertThrows(IllegalArgumentException.class, () -> getWeightedMeritAmount(100_000, 1, -1, 1_000_000));
    }

    @Test
    public void testInvalidBlockPerYear() {
        assertThrows(IllegalArgumentException.class, () -> getWeightedMeritAmount(100_000, 1, 11, -1));
    }
}
