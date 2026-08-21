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

package bisq.core.btc.wallet.validation;

import bisq.core.trade.validation.TransactionValidation;

import org.bitcoinj.core.ECKey;

import static com.google.common.base.Preconditions.checkNotNull;

public final class DelayedPayoutTxSignatureValidation {
    private DelayedPayoutTxSignatureValidation() {
    }

    public static ECKey.ECDSASignature checkCanonicalDelayedPayoutTxSignature(byte[] signature,
                                                                              String signer) {
        byte[] checkedSignature = checkNotNull(signature,
                "%s delayed payout tx signature must not be null",
                signer);
        return TransactionValidation.toVerifiedDerEncodedEcdsaSignature(checkedSignature);
    }
}
