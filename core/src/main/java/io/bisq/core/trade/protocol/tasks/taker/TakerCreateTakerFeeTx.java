/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.trade.protocol.tasks.taker;

import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.arbitration.Arbitrator;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.protocol.ArbitratorSelectionRule;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import io.bisq.core.user.User;
import io.bisq.network.p2p.NodeAddress;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Transaction;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class TakerCreateTakerFeeTx extends TradeTask {
    @SuppressWarnings({"WeakerAccess", "unused"})
    public TakerCreateTakerFeeTx(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            User user = processModel.getUser();
            NodeAddress selectedArbitratorNodeAddress = ArbitratorSelectionRule.select(user.getAcceptedArbitratorAddresses(),
                    processModel.getOffer());
            log.debug("selectedArbitratorAddress " + selectedArbitratorNodeAddress);
            Arbitrator selectedArbitrator = user.getAcceptedArbitratorByAddress(selectedArbitratorNodeAddress);
            checkNotNull(selectedArbitrator, "selectedArbitrator must not be null at CreateTakeOfferFeeTx");
            BtcWalletService walletService = processModel.getWalletService();
            String id = model.getOffer().getId();
            AddressEntry addressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.OFFER_FUNDING);
            AddressEntry reservedForTradeAddressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.RESERVED_FOR_TRADE);
            AddressEntry changeAddressEntry = walletService.getOrCreateAddressEntry(AddressEntry.Context.AVAILABLE);
            Address fundingAddress = addressEntry.getAddress();
            Address reservedForTradeAddress = reservedForTradeAddressEntry.getAddress();
            Address changeAddress = changeAddressEntry.getAddress();
            if (trade.isCurrencyForTakerFeeBtc()) {
                Transaction createTakeOfferFeeTx = processModel.getTradeWalletService().createBtcTradingFeeTx(
                        fundingAddress,
                        reservedForTradeAddress,
                        changeAddress,
                        processModel.getFundsNeededForTrade(),
                        processModel.getUseSavingsWallet(),
                        trade.getTakerFee(),
                        trade.getTxFee(),
                        selectedArbitrator.getBtcAddress());

                processModel.setTakeOfferFeeTx(createTakeOfferFeeTx);
                trade.setTakerFeeTxId(createTakeOfferFeeTx.getHashAsString());

                complete();
            } else {
                // TODO
            }
        } catch (Throwable t) {
            failed(t);
        }
    }
}
