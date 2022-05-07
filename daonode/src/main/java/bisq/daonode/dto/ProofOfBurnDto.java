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

package bisq.daonode.dto;

import lombok.Getter;

/**
 * Minimal data required for Bisq 2 reputation use case.
 * Need to be in sync with the Bisq 2 ProofOfBurnDto class.
 */
@Getter
public class ProofOfBurnDto {
    private String txId;
    private final long burnedAmount;
    private final int blockHeight;
    private final long time;
    private final String hash;

    public ProofOfBurnDto(String txId, long burnedAmount, int blockHeight, long time, String hash) {
        this.txId = txId;
        this.burnedAmount = burnedAmount;
        this.blockHeight = blockHeight;
        this.time = time;
        this.hash = hash;
    }
}
