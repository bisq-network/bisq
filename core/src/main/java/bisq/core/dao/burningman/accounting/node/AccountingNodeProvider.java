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

package bisq.core.dao.burningman.accounting.node;

import bisq.core.dao.burningman.accounting.node.full.AccountingFullNode;
import bisq.core.dao.burningman.accounting.node.lite.AccountingLiteNode;
import bisq.core.user.Preferences;

import bisq.common.config.Config;

import com.google.inject.Inject;

import javax.inject.Named;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AccountingNodeProvider {
    @Getter
    private final AccountingNode accountingNode;

    @Inject
    public AccountingNodeProvider(AccountingLiteNode liteNode,
                                  AccountingFullNode fullNode,
                                  @Named(Config.IS_BM_FULL_NODE) boolean isBmFullNode,
                                  Preferences preferences) {

        boolean rpcDataSet = preferences.getRpcUser() != null &&
                !preferences.getRpcUser().isEmpty()
                && preferences.getRpcPw() != null &&
                !preferences.getRpcPw().isEmpty() &&
                preferences.getBlockNotifyPort() > 0;
        if (isBmFullNode && rpcDataSet) {
            accountingNode = fullNode;
        } else {
            accountingNode = liteNode;
        }
    }
}
