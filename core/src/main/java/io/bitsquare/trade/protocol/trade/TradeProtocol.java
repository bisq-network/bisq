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

package io.bitsquare.trade.protocol.trade;

import io.bitsquare.p2p.MailboxMessage;
import io.bitsquare.p2p.MessageHandler;
import io.bitsquare.trade.TakerTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.states.OffererTradeState;
import io.bitsquare.trade.states.TakerTradeState;

import java.util.Timer;
import java.util.TimerTask;

import javafx.application.Platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class TradeProtocol {
    private static final Logger log = LoggerFactory.getLogger(TradeProtocol.class);
    private static final long TIMEOUT = 10000;

    protected final ProcessModel processModel;
    protected MessageHandler messageHandler;
    protected Timer timeoutTimer;
    protected Trade trade;

    public TradeProtocol(ProcessModel processModel) {
        this.processModel = processModel;
    }

    public void cleanup() {
        log.debug("cleanup " + this);
        stopTimeout();
        processModel.getMessageService().removeMessageHandler(messageHandler);
    }

    abstract public void applyMailboxMessage(MailboxMessage mailboxMessage);

    protected void startTimeout() {
        log.debug("startTimeout");
        stopTimeout();

        timeoutTimer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    log.debug("Timeout reached");
                    if (trade instanceof TakerTrade)
                        trade.setProcessState(TakerTradeState.ProcessState.TIMEOUT);
                    else
                        trade.setProcessState(OffererTradeState.ProcessState.TIMEOUT);
                });
            }
        };

        timeoutTimer.schedule(task, TIMEOUT);
    }

    protected void stopTimeout() {
        log.debug("stopTimeout");
        if (timeoutTimer != null) {
            timeoutTimer.cancel();
            timeoutTimer = null;
        }
    }

    protected void handleTaskRunnerFault(String errorMessage) {
        log.error(errorMessage);
        cleanup();
    }
}
