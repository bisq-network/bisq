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

package bisq.core.btc.wallet;

import bisq.core.btc.model.RawTransactionInput;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptPattern;

public class WalletUtils {
    public static boolean isP2WH(RawTransactionInput rawTransactionInput, NetworkParameters params) {
        TransactionOutput connectedOutput = getConnectedOutPoint(rawTransactionInput, params).getConnectedOutput();
        if (connectedOutput == null) {
            return false;
        }
        Script scriptPubKey = connectedOutput.getScriptPubKey();
        if (scriptPubKey == null) {
            return false;
        }
        return ScriptPattern.isP2WH(scriptPubKey);
    }

    public static TransactionOutPoint getConnectedOutPoint(RawTransactionInput rawTransactionInput,
                                                           NetworkParameters params) {
        return new TransactionOutPoint(params, rawTransactionInput.index,
                new Transaction(params, rawTransactionInput.parentTransaction));
    }
}
