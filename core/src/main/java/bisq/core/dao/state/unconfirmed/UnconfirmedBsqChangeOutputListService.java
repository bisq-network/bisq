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

package bisq.core.dao.state.unconfirmed;

import bisq.core.dao.state.model.blockchain.TxType;

import bisq.common.app.DevEnv;
import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.persistable.PersistedDataHost;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.wallet.Wallet;

import javax.inject.Inject;

import java.util.List;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UnconfirmedBsqChangeOutputListService implements PersistedDataHost {
    private final UnconfirmedBsqChangeOutputList unconfirmedBsqChangeOutputList = new UnconfirmedBsqChangeOutputList();
    private final PersistenceManager<UnconfirmedBsqChangeOutputList> persistenceManager;

    @Inject
    public UnconfirmedBsqChangeOutputListService(PersistenceManager<UnconfirmedBsqChangeOutputList> persistenceManager) {
        this.persistenceManager = persistenceManager;

        this.persistenceManager.initialize(unconfirmedBsqChangeOutputList, PersistenceManager.Source.PRIVATE);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted(Runnable completeHandler) {
        if (DevEnv.isDaoActivated()) {
            persistenceManager.readPersisted(persisted -> {
                        unconfirmedBsqChangeOutputList.setAll(persisted.getList());
                        completeHandler.run();
                    },
                    completeHandler);
        } else {
            completeHandler.run();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Once a tx gets committed to our BSQ wallet we store the change output for allowing it to be spent in follow-up
     * transactions.
     */
    public void onCommitTx(Transaction tx, TxType txType, Wallet wallet) {
        // We remove all potential connected outputs from our inputs as they would have been spent.
        removeConnectedOutputsOfInputsOfTx(tx);

        int changeOutputIndex;
        switch (txType) {
            case UNDEFINED_TX_TYPE:
            case UNVERIFIED:
            case INVALID:
            case GENESIS:
                return;
            case TRANSFER_BSQ:
                changeOutputIndex = 1; // output 0 is receiver's address
                break;
            case PAY_TRADE_FEE:
                changeOutputIndex = 0;
                break;
            case PROPOSAL:
                changeOutputIndex = 0;
                break;
            case COMPENSATION_REQUEST:
            case REIMBURSEMENT_REQUEST:
                changeOutputIndex = 0;
                break;
            case BLIND_VOTE:
                changeOutputIndex = 1; // output 0 is stake
                break;
            case VOTE_REVEAL:
                changeOutputIndex = 0;
                break;
            case LOCKUP:
                changeOutputIndex = 1; // output 0 is lockup amount
                break;
            case UNLOCK:
                // We don't allow to spend the unlocking funds as there is the lock time which need to pass,
                // otherwise the funds get burned!
                return;
            case ASSET_LISTING_FEE:
                changeOutputIndex = 0;
                break;
            case PROOF_OF_BURN:
                changeOutputIndex = 0;
                break;
            case IRREGULAR:
                return;
            default:
                return;
        }

        // It can be that we don't have a BSQ and a BTC change output.
        // If no BSQ change but a BTC change the index points to the BTC output and then
        // we detect that it is not part of our wallet.
        // If there is a BSQ change but no BTC change it has no effect as we ignore BTC outputs anyway.
        // If both change outputs do not exist then we might point to an index outside
        // of the list and we return at our scope check.

        // If no BTC output (unlikely but
        // possible) the index points to the BTC output and then we detect that it is not part of our wallet.
        //
        List<TransactionOutput> outputs = tx.getOutputs();
        if (changeOutputIndex > outputs.size() - 1)
            return;

        TransactionOutput change = outputs.get(changeOutputIndex);
        if (!change.isMine(wallet))
            return;

        UnconfirmedTxOutput txOutput = UnconfirmedTxOutput.fromTransactionOutput(change);
        if (unconfirmedBsqChangeOutputList.containsTxOutput(txOutput))
            return;

        unconfirmedBsqChangeOutputList.add(txOutput);
        requestPersistence();
    }

    public void onReorganize() {
        reset();
    }

    public void onSpvResync() {
        reset();
    }

    public void onTransactionConfidenceChanged(Transaction tx) {
        if (tx != null &&
                tx.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING) {
            removeConnectedOutputsOfInputsOfTx(tx);

            tx.getOutputs().forEach(transactionOutput -> {
                UnconfirmedTxOutput txOutput = UnconfirmedTxOutput.fromTransactionOutput(transactionOutput);
                if (unconfirmedBsqChangeOutputList.containsTxOutput(txOutput)) {
                    unconfirmedBsqChangeOutputList.remove(txOutput);
                }
            });
        }
    }

    public boolean hasTransactionOutput(TransactionOutput output) {
        return unconfirmedBsqChangeOutputList.containsTxOutput(UnconfirmedTxOutput.fromTransactionOutput(output));
    }

    public Coin getBalance() {
        return Coin.valueOf(unconfirmedBsqChangeOutputList.stream().mapToLong(UnconfirmedTxOutput::getValue).sum());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void removeConnectedOutputsOfInputsOfTx(Transaction tx) {
        tx.getInputs().stream()
                .map(TransactionInput::getConnectedOutput)
                .filter(Objects::nonNull)
                .map(UnconfirmedTxOutput::fromTransactionOutput)
                .filter(unconfirmedBsqChangeOutputList::containsTxOutput)
                .forEach(txOutput -> {
                    unconfirmedBsqChangeOutputList.remove(txOutput);
                    requestPersistence();
                });
    }

    private void reset() {
        unconfirmedBsqChangeOutputList.clear();
        requestPersistence();
    }

    private void requestPersistence() {
        persistenceManager.requestPersistence();
    }
}
