/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade.protocol.trade.tasks.offerer;

import io.bitsquare.btc.WalletService;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.OffererTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.tasks.TradeTask;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
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

    public SetupDepositBalanceListener(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            WalletService walletService = processModel.getWalletService();
            Address address = walletService.getAddressEntryByOfferId(trade.getId()).getAddress();
            balanceListener = walletService.addBalanceListener(new BalanceListener(address) {
                @Override
                public void onBalanceChanged(Coin balance) {
                    updateBalance(balance);
                }
            });
            walletService.addBalanceListener(balanceListener);

            tradeStateSubscription = EasyBind.subscribe(trade.stateProperty(), newValue -> {
                log.debug("tradeStateSubscription newValue " + newValue);
                if (newValue == Trade.State.DEPOSIT_PUBLISHED_MSG_RECEIVED
                        || newValue == Trade.State.DEPOSIT_SEEN_IN_NETWORK) {

                    walletService.removeBalanceListener(balanceListener);
                    log.debug(" UserThread.execute(this::unSubscribe);");
                    // TODO is that allowed?
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
        //TODO investigate, seems to not get called sometimes
        log.debug("unSubscribe tradeStateSubscription");
        tradeStateSubscription.unsubscribe();
    }

    private void updateBalance(Coin balance) {
        log.debug("updateBalance " + balance.toFriendlyString());
        log.debug("pre tradeState " + trade.getState().toString());
        Trade.State tradeState = trade.getState();
        if (balance.compareTo(Coin.ZERO) == 0) {
            if (trade instanceof OffererTrade) {
                processModel.getOpenOfferManager().closeOpenOffer(trade.getOffer());

                if (tradeState == Trade.State.DEPOSIT_PUBLISH_REQUESTED) {
                    trade.setState(Trade.State.DEPOSIT_SEEN_IN_NETWORK);
                } else if (tradeState.getPhase() == Trade.Phase.PREPARATION) {
                    processModel.getTradeManager().removePreparedTrade(trade);
                } else if (tradeState.getPhase().ordinal() < Trade.Phase.DEPOSIT_PAID.ordinal()) {
                    processModel.getTradeManager().addTradeToFailedTrades(trade);
                }
            }
        }

        log.debug("tradeState " + trade.getState().toString());
    }
}
