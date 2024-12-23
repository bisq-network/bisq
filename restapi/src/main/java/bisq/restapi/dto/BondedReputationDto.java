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

package bisq.restapi.dto;

import lombok.Getter;
import lombok.ToString;



import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Minimal data required for Bisq 2 bonded reputation use case.
 * Need to be in sync with the Bisq 2 BondedReputationDto class.
 */
@Getter
@ToString
@Schema(title = "BondedReputation")
public class BondedReputationDto {
    private final long amount;
    private final long blockTime;
    private final String hash;
    private final int blockHeight;
    private final int lockTime;
    private final String txId;

    public BondedReputationDto(long amount, long blockTime, String hash, int blockHeight, int lockTime, String txId) {
        this.amount = amount;
        this.blockTime = blockTime;
        this.hash = hash;
        this.blockHeight = blockHeight;
        this.lockTime = lockTime;
        this.txId = txId;
    }
}
