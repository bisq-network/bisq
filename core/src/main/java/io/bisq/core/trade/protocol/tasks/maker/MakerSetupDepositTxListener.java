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

package io.bisq.core.trade.protocol.tasks.maker;

import io.bisq.common.UserThread;
import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.listeners.BalanceListener;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

@Slf4j
public class MakerSetupDepositTxListener extends TradeTask {
    // Use instance fields to not get eaten up by the GC
    private Subscription tradeStateSubscription;
    private BalanceListener listener;

    @SuppressWarnings({"WeakerAccess", "unused"})
    public MakerSetupDepositTxListener(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            if (trade.getState().getPhase() == Trade.Phase.TAKER_FEE_PUBLISHED) {
                BtcWalletService walletService = processModel.getBtcWalletService();
                final String id = trade.getId();
                Address address = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.RESERVED_FOR_TRADE).getAddress();

                if (walletService.getBalanceForAddress(address).isZero()) {
                    trade.setState(Trade.State.MAKER_SAW_DEPOSIT_TX_IN_NETWORK);
                    swapReservedForTradeEntry();
                } else {
                    listener = new BalanceListener(address) {
                        @Override
                        public void onBalanceChanged(Coin balance, Transaction tx) {
                            if (balance.isZero() && trade.getState().getPhase() == Trade.Phase.TAKER_FEE_PUBLISHED) {
                                trade.setState(Trade.State.MAKER_SAW_DEPOSIT_TX_IN_NETWORK);
                                swapReservedForTradeEntry();
                            }
                        }
                    };
                    walletService.addBalanceListener(listener);

                    tradeStateSubscription = EasyBind.subscribe(trade.stateProperty(), newValue -> {
                        log.error("MakerSetupDepositTxListener tradeStateSubscription tradeState=" + newValue);
                        if (newValue.getPhase() != Trade.Phase.TAKER_FEE_PUBLISHED) {
                            walletService.removeBalanceListener(listener);
                            swapReservedForTradeEntry();
                            // hack to remove tradeStateSubscription at callback
                            UserThread.execute(this::unSubscribe);
                        }
                    });

                }
            }

            // we complete immediately, our object stays alive because the balanceListener is stored in the WalletService
            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }

    private void swapReservedForTradeEntry() {
        log.error("swapReservedForTradeEntry, offerid={}, RESERVED_FOR_TRADE", trade.getId());
        processModel.getBtcWalletService().swapTradeEntryToAvailableEntry(trade.getId(), AddressEntry.Context.RESERVED_FOR_TRADE);
    }

    private void unSubscribe() {
        if (tradeStateSubscription != null)
            tradeStateSubscription.unsubscribe();
    }
}
