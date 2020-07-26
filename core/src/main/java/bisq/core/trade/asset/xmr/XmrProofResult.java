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

package bisq.core.trade.asset.xmr;

public enum XmrProofResult {
    TX_NOT_CONFIRMED,
    PROOF_OK,
    UNKNOWN_ERROR,
    TX_KEY_REUSED,
    TX_NEVER_FOUND,
    TX_HASH_INVALID,
    TX_KEY_INVALID,
    ADDRESS_INVALID,
    AMOUNT_NOT_MATCHING,
    PROOF_FAILED
}
