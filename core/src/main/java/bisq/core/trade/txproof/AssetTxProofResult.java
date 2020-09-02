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

package bisq.core.trade.txproof;

import lombok.Getter;

public enum AssetTxProofResult {
    UNDEFINED,

    FEATURE_DISABLED,
    TRADE_LIMIT_EXCEEDED,
    INVALID_DATA,  // Peer provided invalid data. Might be a scam attempt (e.g. txKey reused)
    PAYOUT_TX_ALREADY_PUBLISHED,

    REQUESTS_STARTED(false),
    PENDING(false),

    // All services completed with a success state
    COMPLETED,

    // Any service had an error (network, API service)
    ERROR,

    // Any service failed. Might be that the tx is invalid.
    FAILED;

    @Getter
    private int numSuccessResults;
    @Getter
    private int numRequiredSuccessResults;
    @Getter
    private String details = "";
    // If isTerminal is set it means that we stop the service
    @Getter
    private final boolean isTerminal;

    AssetTxProofResult() {
        this(false);
    }

    AssetTxProofResult(boolean isTerminal) {
        this.isTerminal = isTerminal;
    }


    public AssetTxProofResult numSuccessResults(int numSuccessResults) {
        this.numSuccessResults = numSuccessResults;
        return this;
    }

    public AssetTxProofResult numRequiredSuccessResults(int numRequiredSuccessResults) {
        this.numRequiredSuccessResults = numRequiredSuccessResults;
        return this;
    }

    public AssetTxProofResult details(String details) {
        this.details = details;
        return this;
    }

    @Override
    public String toString() {
        return "AssetTxProofResult{" +
                "\n     numSuccessResults=" + numSuccessResults +
                ",\n     requiredSuccessResults=" + numRequiredSuccessResults +
                ",\n     details='" + details + '\'' +
                "\n} " + super.toString();
    }
}
