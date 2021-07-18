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

package bisq.core.trade.protocol.tasks.buyer;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletService;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.trade.Contract;
import bisq.core.trade.Trade;
import bisq.core.trade.messages.DepositTxAndDelayedPayoutTxMessage;
import bisq.core.trade.protocol.ProcessModel;
import bisq.core.trade.protocol.tasks.TradeTask;
import bisq.core.util.Validator;

import bisq.common.crypto.Hash;
import bisq.common.crypto.Sig;
import bisq.common.taskrunner.TaskRunner;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.Wallet;

import java.util.Arrays;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class BuyerProcessDepositTxAndDelayedPayoutTxMessage extends TradeTask {
    public BuyerProcessDepositTxAndDelayedPayoutTxMessage(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            var message = checkNotNull((DepositTxAndDelayedPayoutTxMessage) processModel.getTradeMessage());
            checkNotNull(message);
            Validator.checkTradeId(processModel.getOfferId(), message);

            // To access tx confidence we need to add that tx into our wallet.
            byte[] depositTxBytes = checkNotNull(message.getDepositTx());
            Transaction depositTx = processModel.getBtcWalletService().getTxFromSerializedTx(depositTxBytes);
            // update with full tx
            Wallet wallet = processModel.getBtcWalletService().getWallet();
            Transaction committedDepositTx = WalletService.maybeAddSelfTxToWallet(depositTx, wallet);
            trade.applyDepositTx(committedDepositTx);
            BtcWalletService.printTx("depositTx received from peer", committedDepositTx);

            // To access tx confidence we need to add that tx into our wallet.
            byte[] delayedPayoutTxBytes = checkNotNull(message.getDelayedPayoutTx());
            checkArgument(Arrays.equals(delayedPayoutTxBytes, trade.getDelayedPayoutTxBytes()),
                    "mismatch between delayedPayoutTx received from peer and our one." +
                            "\n Expected: " + Utilities.bytesAsHexString(trade.getDelayedPayoutTxBytes()) +
                            "\n Received: " + Utilities.bytesAsHexString(delayedPayoutTxBytes));
            trade.applyDelayedPayoutTxBytes(delayedPayoutTxBytes);

            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());

            PaymentAccountPayload sellerPaymentAccountPayload = message.getSellerPaymentAccountPayload();
            if (sellerPaymentAccountPayload != null) {
                byte[] sellerPaymentAccountPayloadHash = ProcessModel.hashOfPaymentAccountPayload(sellerPaymentAccountPayload);
                Contract contract = trade.getContract();
                byte[] peersPaymentAccountPayloadHash = checkNotNull(contract).getHashOfPeersPaymentAccountPayload(processModel.getPubKeyRing());
                checkArgument(Arrays.equals(sellerPaymentAccountPayloadHash, peersPaymentAccountPayloadHash),
                        "Hash of payment account is invalid");

                processModel.getTradingPeer().setPaymentAccountPayload(sellerPaymentAccountPayload);
                contract.setPaymentAccountPayloads(sellerPaymentAccountPayload,
                        processModel.getPaymentAccountPayload(trade),
                        processModel.getPubKeyRing());

                // As we have added the payment accounts we need to update the json. We also update the signature
                // thought that has less relevance with the changes of 1.7.0
                String contractAsJson = Utilities.objectToJson(contract);
                String signature = Sig.sign(processModel.getKeyRing().getSignatureKeyPair().getPrivate(), contractAsJson);
                trade.setContractAsJson(contractAsJson);
                if (contract.isBuyerMakerAndSellerTaker()) {
                    trade.setMakerContractSignature(signature);
                } else {
                    trade.setTakerContractSignature(signature);
                }

                byte[] contractHash = Hash.getSha256Hash(checkNotNull(trade.getContractAsJson()));
                trade.setContractHash(contractHash);
            }

            // If we got already the confirmation we don't want to apply an earlier state
            if (trade.getState().ordinal() < Trade.State.BUYER_SAW_DEPOSIT_TX_IN_NETWORK.ordinal()) {
                trade.setState(Trade.State.BUYER_RECEIVED_DEPOSIT_TX_PUBLISHED_MSG);
            }

            processModel.getBtcWalletService().swapTradeEntryToAvailableEntry(trade.getId(),
                    AddressEntry.Context.RESERVED_FOR_TRADE);

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
