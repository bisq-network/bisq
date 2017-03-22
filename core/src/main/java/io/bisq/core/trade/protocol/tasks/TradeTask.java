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

package io.bisq.core.trade.protocol.tasks;

import io.bisq.common.taskrunner.Task;
import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.protocol.ProcessModel;
import io.bisq.network.p2p.DecryptedMsgWithPubKey;
import io.bisq.protobuffer.message.p2p.MailboxMessage;
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
