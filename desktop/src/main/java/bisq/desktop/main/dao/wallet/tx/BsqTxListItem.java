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

package bisq.desktop.main.dao.wallet.tx;

import bisq.desktop.components.TxConfidenceListItem;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;

import java.util.Date;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
@EqualsAndHashCode(callSuper = true)
@Data
class BsqTxListItem extends TxConfidenceListItem {
    private final DaoFacade daoFacade;
    private final BsqFormatter bsqFormatter;
    private final Date date;
    private final boolean isBurnedBsqTx;
    private final boolean withdrawalToBTCWallet;

    private final String address;
    private final String direction;
    private Coin amount;
    private boolean received;

    private boolean issuanceTx;

    BsqTxListItem(Transaction transaction,
                  BsqWalletService bsqWalletService,
                  BtcWalletService btcWalletService,
                  DaoFacade daoFacade,
                  Date date,
                  BsqFormatter bsqFormatter) {
        super(transaction, bsqWalletService);

        this.daoFacade = daoFacade;
        this.isBurnedBsqTx = daoFacade.hasTxBurntFee(transaction.getHashAsString());
        this.date = date;
        this.bsqFormatter = bsqFormatter;

        checkNotNull(transaction, "transaction must not be null as we only have list items from transactions " +
                "which are available in the wallet");

        Coin valueSentToMe = bsqWalletService.getValueSentToMeForTransaction(transaction);
        Coin valueSentFromMe = bsqWalletService.getValueSentFromMeForTransaction(transaction);
        Coin valueSentToMyBTCWallet = btcWalletService.getValueSentToMeForTransaction(transaction);
        Coin valueSentFromMyBTCWallet = btcWalletService.getValueSentFromMeForTransaction(transaction);

        withdrawalToBTCWallet = valueSentToMyBTCWallet.getValue() > valueSentFromMyBTCWallet.getValue();

        amount = valueSentToMe.subtract(valueSentFromMe);
        if (amount.isPositive()) {
            if (txId.equals(daoFacade.getGenesisTxId()))
                direction = Res.get("funds.tx.direction.genesisTx");
            else
                direction = Res.get("funds.tx.direction.receivedWith");

            received = true;
        } else if (amount.isNegative()) {
            direction = Res.get("funds.tx.direction.sentTo");
            received = false;
        } else {
            // Self send
            direction = "";
        }

        String sendToAddress = null;
        for (TransactionOutput output : transaction.getOutputs()) {
            if (!bsqWalletService.isTransactionOutputMine(output) &&
                    !btcWalletService.isTransactionOutputMine(output) &&
                    WalletService.isOutputScriptConvertibleToAddress(output)) {
                // We don't support send txs with multiple outputs to multiple receivers, so we can
                // assume that only one output is not from our own wallets.
                sendToAddress = bsqFormatter.getBsqAddressStringFromAddress(WalletService.getAddressFromOutput(output));
                break;
            }
        }

        // In the case we sent to ourselves (either to BSQ or BTC wallet) we show the first as the other is
        // usually the change output.
        String receivedWithAddress = Res.get("shared.na");
        if (sendToAddress != null) {
            for (TransactionOutput output : transaction.getOutputs()) {
                if (WalletService.isOutputScriptConvertibleToAddress(output)) {
                    receivedWithAddress = bsqFormatter.getBsqAddressStringFromAddress(WalletService.getAddressFromOutput(output));
                    break;
                }
            }
        }

        if (!isBurnedBsqTx)
            address = received ? receivedWithAddress : sendToAddress;
        else
            address = "";
    }

    public TxType getTxType() {
        return daoFacade.getTx(txId)
                .flatMap(tx -> daoFacade.getOptionalTxType(tx.getId()))
                .orElse(confirmations == 0 ? TxType.UNVERIFIED : TxType.UNDEFINED_TX_TYPE);
    }

    public boolean isWithdrawalToBTCWallet() {
        return withdrawalToBTCWallet;
    }
}

