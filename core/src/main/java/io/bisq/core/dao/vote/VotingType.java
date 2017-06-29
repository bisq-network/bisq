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

public enum VotingType {
    MAKER_FEE_IN_BTC((byte) 0x01),
    TAKER_FEE_IN_BTC((byte) 0x02),
    MAKER_FEE_IN_BSQ((byte) 0x03),
    TAKER_FEE_IN_BSQ((byte) 0x04),
    CREATE_COMPENSATION_REQUEST_FEE_IN_BSQ((byte) 0x05),
    VOTING_FEE_IN_BSQ((byte) 0x06),

    COMPENSATION_REQUEST_PERIOD_IN_BLOCKS((byte) 0x10),
    VOTING_PERIOD_IN_BLOCKS((byte) 0x11),
    FUNDING_PERIOD_IN_BLOCKS((byte) 0x12),
    BREAK_BETWEEN_PERIODS_IN_BLOCKS((byte) 0x13),

    QUORUM_FOR_COMPENSATION_REQUEST_VOTING((byte) 0x20),
    QUORUM_FOR_PARAMETER_VOTING((byte) 0x21),

    MIN_BTC_AMOUNT_COMPENSATION_REQUEST((byte) 0x30),
    MAX_BTC_AMOUNT_COMPENSATION_REQUEST((byte) 0x31),

    CONVERSION_RATE((byte) 0x40),

    COMP_REQUEST_MAPS((byte) 0x50);

    // TODO max growth rate of BSQ

    public final Byte code;

    VotingType(Byte code) {
        this.code = code;
    }
}
