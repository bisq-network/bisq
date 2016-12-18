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

package io.bitsquare.dao.vote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VotingCodes {
    private static final Logger log = LoggerFactory.getLogger(VotingCodes.class);

    public enum Code {
        COMP_REQUEST_MAPS((byte) 0x01, 2),
        CREATE_OFFER_FEE((byte) 0x11, 1),
        TAKE_OFFER_FEE((byte) 0x12, 1),
        PERIOD_UNTIL_NEXT_VOTING((byte) 0x20, 1);

        public final Byte code;
        public final int payloadSize;

        Code(Byte code, int payloadSize) {
            this.code = code;
            this.payloadSize = payloadSize;
        }
    }

}
