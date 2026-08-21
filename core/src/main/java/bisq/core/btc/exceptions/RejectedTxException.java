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

package bisq.core.btc.exceptions;

import org.bitcoinj.core.RejectMessage;

import lombok.Getter;

import javax.annotation.Nullable;

public class RejectedTxException extends RuntimeException {
    @Getter
    private final RejectMessage rejectMessage;
    @Getter
    @Nullable
    private final String txId;

    public RejectedTxException(String message, RejectMessage rejectMessage) {
        super(message);
        this.rejectMessage = rejectMessage;
        txId = rejectMessage.getRejectedObjectHash() != null ? rejectMessage.getRejectedObjectHash().toString() : null;
    }

    @Override
    public String toString() {
        return "RejectedTxException{" +
                "\n     rejectMessage=" + rejectMessage +
                ",\n     txId='" + txId + '\'' +
                "\n} " + super.toString();
    }
}
