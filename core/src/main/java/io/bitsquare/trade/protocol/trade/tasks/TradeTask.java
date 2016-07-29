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

package io.bitsquare.trade.protocol.trade.tasks;

import io.bitsquare.common.taskrunner.Task;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.crypto.DecryptedMsgWithPubKey;
import io.bitsquare.p2p.messaging.MailboxMessage;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.ProcessModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class TradeTask extends Task<Trade> {
    private static final Logger log = LoggerFactory.getLogger(TradeTask.class);

    protected final ProcessModel processModel;
    protected final Trade trade;

    protected TradeTask(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);

        this.trade = trade;
        processModel = trade.getProcessModel();
    }

    @Override
    protected void failed() {
        trade.setErrorMessage(errorMessage);
        super.failed();
    }

    @Override
    protected void failed(String message) {
        appendToErrorMessage(message);
        trade.setErrorMessage(errorMessage);
        super.failed();
    }

    @Override
    protected void failed(Throwable t) {
        t.printStackTrace();
        appendExceptionToErrorMessage(t);
        trade.setErrorMessage(errorMessage);
        super.failed();
    }

    protected void removeMailboxMessageAfterProcessing() {
        if (processModel.getTradeMessage() instanceof MailboxMessage) {
            DecryptedMsgWithPubKey mailboxMessage = trade.getMailboxMessage();
            if (mailboxMessage != null && mailboxMessage.message.equals(processModel.getTradeMessage())) {
                log.debug("Remove mailboxMessage from P2P network. mailboxMessage = " + mailboxMessage);
                processModel.getP2PService().removeEntryFromMailbox(mailboxMessage);
                trade.setMailboxMessage(null);
            }
        }
    }
}
