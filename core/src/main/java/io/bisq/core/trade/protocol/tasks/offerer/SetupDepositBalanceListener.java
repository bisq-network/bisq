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

package io.bisq.core.trade.protocol.tasks.offerer;

import io.bisq.common.UserThread;
import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.listeners.BalanceListener;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.trade.OffererTrade;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// The buyer waits for the msg from the seller that he has published the deposit tx.
// In error case he might not get that msg so we check additionally the balance of our inputs, if it is zero, it means the deposit
// is already published. We set then the DEPOSIT_LOCKED state, so the user get informed that he is already in the critical state and need
// to request support.
public class SetupDepositBalanceListener extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(SetupDepositBalanceListener.class);
    private Subscription tradeStateSubscription;
    private BalanceListener balanceListener;

    @SuppressWarnings({"WeakerAccess", "unused"})
    public SetupDepositBalanceListener(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            BtcWalletService walletService = processModel.getWalletService();
            Address address = walletService.getOrCreateAddressEntry(trade.getId(), AddressEntry.Context.RESERVED_FOR_TRADE).getAddress();
            balanceListener = new BalanceListener(address) {
                @Override
                public void onBalanceChanged(Coin balance, Transaction tx) {
                    updateBalance(balance);
                }
            };
            walletService.addBalanceListener(balanceListener);

            tradeStateSubscription = EasyBind.subscribe(trade.stateProperty(), newValue -> {
                log.debug("tradeStateSubscription newValue " + newValue);
                if (newValue == Trade.State.OFFERER_RECEIVED_DEPOSIT_TX_PUBLISHED_MSG
                        || newValue == Trade.State.DEPOSIT_SEEN_IN_NETWORK) {

                    walletService.removeBalanceListener(balanceListener);
                    // hack to remove tradeStateSubscription at callback
                    UserThread.execute(this::unSubscribe);
                }
            });
            updateBalance(walletService.getBalanceForAddress(address));

            // we complete immediately, our object stays alive because the balanceListener is stored in the WalletService
            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }

    private void unSubscribe() {
        tradeStateSubscription.unsubscribe();
    }

    private void updateBalance(Coin balance) {
        log.debug("updateBalance " + balance.toFriendlyString());
        log.debug("pre tradeState " + trade.getState().toString());
        Trade.State tradeState = trade.getState();
        if (balance.compareTo(Coin.ZERO) == 0) {
            if (trade instanceof OffererTrade) {
                processModel.getOpenOfferManager().closeOpenOffer(trade.getOffer());

                if (tradeState == Trade.State.OFFERER_SENT_PUBLISH_DEPOSIT_TX_REQUEST) {
                    trade.setState(Trade.State.DEPOSIT_SEEN_IN_NETWORK);
                } else if (tradeState.getPhase() == Trade.Phase.PREPARATION) {
                    processModel.getTradeManager().removePreparedTrade(trade);
                } else if (tradeState.getPhase().ordinal() < Trade.Phase.DEPOSIT_PAID.ordinal()) {
                    // TODO need to evaluate if that is correct
                    processModel.getTradeManager().addTradeToFailedTrades(trade);
                }
            }
        }

        log.debug("tradeState " + trade.getState().toString());
    }
}
