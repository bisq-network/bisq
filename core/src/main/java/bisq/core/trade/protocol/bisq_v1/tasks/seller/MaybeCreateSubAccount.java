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

package bisq.core.trade.protocol.bisq_v1.tasks.seller;

import bisq.core.payment.AssetAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.XmrAccountDelegate;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MaybeCreateSubAccount extends TradeTask {

    public MaybeCreateSubAccount(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            PaymentAccount parentAccount = Objects.requireNonNull(processModel.getPaymentAccount());

            // This is a seller task, so no need to check for it
            if (!trade.getOffer().isXmr() ||
                    parentAccount.getExtraData() == null ||
                    parentAccount.getExtraData().isEmpty() ||
                    !XmrAccountDelegate.isUsingSubAddresses(parentAccount)) {
                complete();
                return;
            }

            // In case we are a seller using XMR sub addresses we clone the account, add it as xmrAccount and
            // increment from the highest subAddressIndex from all our subAccounts grouped by the subAccountId (mainAddress + accountIndex).
            PaymentAccount paymentAccount = processModel.getTradeManager().cloneAccount(Objects.requireNonNull(parentAccount));
            XmrAccountDelegate xmrAccountDelegate = new XmrAccountDelegate((AssetAccount) paymentAccount);
            // We overwrite some fields
            xmrAccountDelegate.setId(UUID.randomUUID().toString());
            xmrAccountDelegate.setTradeId(trade.getId());
            xmrAccountDelegate.setCreationDate(new Date().getTime());
            // We add our cloned account as xmrAccount and apply the incremented index and subAddress.

            // We need to store that globally, so we use the user object.
            Map<String, Set<PaymentAccount>> subAccountsBySubAccountId = processModel.getUser().getSubAccountsById();
            subAccountsBySubAccountId.putIfAbsent(xmrAccountDelegate.getSubAccountId(), new HashSet<>());
            Set<PaymentAccount> subAccounts = subAccountsBySubAccountId.get(xmrAccountDelegate.getSubAccountId());

            // At first subAccount we use the index of the parent account and decrement by 1 as we will increment later in the code
            long initialSubAccountIndex = xmrAccountDelegate.getSubAddressIndexAsLong() - 1;
            long maxSubAddressIndex = subAccounts.stream()
                    .mapToLong(XmrAccountDelegate::getSubAddressIndexAsLong)
                    .max()
                    .orElse(initialSubAccountIndex);

            // Always increment, use the (decremented) initialSubAccountIndex or the next after max
            ++maxSubAddressIndex;

            // Prefix subAddressIndex to account name
            xmrAccountDelegate.setAccountName("[" + maxSubAddressIndex + "] " + parentAccount.getAccountName());
            xmrAccountDelegate.setSubAddressIndex(String.valueOf(maxSubAddressIndex));
            xmrAccountDelegate.createAndSetNewSubAddress();
            subAccounts.add(xmrAccountDelegate.getAccount());

            // Now we set our xmrAccount as paymentAccount
            processModel.setPaymentAccount(xmrAccountDelegate.getAccount());
            // We got set the accountId from the parent account at the ProcessModel constructor. We update it to the subAccounts id.
            processModel.setAccountId(xmrAccountDelegate.getId());
            processModel.getUser().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}

