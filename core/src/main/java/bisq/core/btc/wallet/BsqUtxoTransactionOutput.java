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

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.UTXO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BsqUtxoTransactionOutput extends TransactionOutput {
    private static final Logger log = LoggerFactory.getLogger(BsqUtxoTransactionOutput.class);
    private final UTXO output;
    private final int chainHeight;

    /**
     * Construct a free standing Transaction Output.
     *
     * @param params The network parameters.
     * @param output The stored output (free standing).
     */
    public BsqUtxoTransactionOutput(NetworkParameters params, UTXO output, int chainHeight) {
        super(params, null, output.getValue(), output.getScript().getProgram());
        this.output = output;
        this.chainHeight = chainHeight;
    }

    /**
     * Get the {@link UTXO}.
     *
     * @return The stored output.
     */
    public UTXO getUTXO() {
        return output;
    }

    /**
     * Get the depth withing the chain of the parent tx, depth is 1 if it the output height is the height of
     * the latest block.
     *
     * @return The depth.
     */
    @Override
    public int getParentTransactionDepthInBlocks() {
        return chainHeight - output.getHeight() + 1;
    }

    @Override
    public int getIndex() {
        return (int) output.getIndex();
    }

    @Override
    public Sha256Hash getParentTransactionHash() {
        return output.getHash();
    }
}
