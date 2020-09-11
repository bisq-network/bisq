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

package bisq.core.trade.protocol.tasks.seller;

import bisq.core.account.sign.SignedWitness;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.trade.Trade;
import bisq.core.trade.messages.TradeMessage;
import bisq.core.trade.messages.TraderSignedWitnessMessage;
import bisq.core.trade.protocol.tasks.SendMailboxMessageTask;

import bisq.common.taskrunner.TaskRunner;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SellerMaybeSignWitnessAndSendWitnessMessage extends SendMailboxMessageTask {
    private SignedWitness signedWitness;

    @SuppressWarnings({"unused"})
    public SellerMaybeSignWitnessAndSendWitnessMessage(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected TradeMessage getMessage(String id) {
        return new TraderSignedWitnessMessage(trade.getId(), processModel.getMyNodeAddress(), signedWitness);
    }

    @Override
    protected void setStateSent() {
        log.info("Sent TraderSignedWitnessMessage: tradeId={} at peer {} SignedWitness {}",
                trade.getId(), trade.getTradingPeerNodeAddress(), signedWitness);
    }

    @Override
    protected void setStateArrived() {
        log.info("TraderSignedWitnessMessage arrived: tradeId={} at peer {} SignedWitness {}",
                trade.getId(), trade.getTradingPeerNodeAddress(), signedWitness);
    }

    @Override
    protected void setStateStoredInMailbox() {
        log.info("TraderSignedWitnessMessage storedInMailbox: tradeId={} at peer {} SignedWitness {}",
                trade.getId(), trade.getTradingPeerNodeAddress(), signedWitness);
    }

    @Override
    protected void setStateFault() {
        log.error("TraderSignedWitnessMessage failed: tradeId={} at peer {} SignedWitness {}",
                trade.getId(), trade.getTradingPeerNodeAddress(), signedWitness);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            AccountAgeWitnessService accountAgeWitnessService = processModel.getAccountAgeWitnessService();
            if (!accountAgeWitnessService.isSignWitnessTrade(trade)) {
                log.warn("Is not a witness trade");
                complete();
                return;
            }

            // Broadcast is done in accountAgeWitness domain.
            Optional<SignedWitness> signedWitnessOptional = accountAgeWitnessService.traderSignAndPublishPeersAccountAgeWitness(trade);
            if (!signedWitnessOptional.isPresent()) {
                log.warn("signedWitnessOptional is not present");
                // TODO not sure if that is a failure case or a valid case, but this follows existing impl logic
                complete();
                return;
            }

            signedWitness = signedWitnessOptional.get();

            // Message sending is handled in base class (SendMailboxMessageTask).
            super.run();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
