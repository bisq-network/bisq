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

import bisq.core.dao.burningman.accounting.BurningManAccountingService;
import bisq.core.dao.burningman.accounting.node.full.AccountingBlockParser;
import bisq.core.dao.state.DaoStateService;
import bisq.core.user.Preferences;

import bisq.network.p2p.P2PService;

import com.google.inject.Inject;

import javax.inject.Singleton;

// Dummy implementation for a do-nothing AccountingNode. Used for the time before the burningman domain gets activated.
@Singleton
class InActiveAccountingNode extends AccountingNode {
    @Inject
    public InActiveAccountingNode(P2PService p2PService,
                                  DaoStateService daoStateService,
                                  BurningManAccountingService burningManAccountingService,
                                  AccountingBlockParser accountingBlockParser,
                                  Preferences preferences) {
        super(p2PService, daoStateService, burningManAccountingService,
                accountingBlockParser, preferences);
    }

    @Override
    public void addListeners() {
    }

    @Override
    public void start() {
    }

    @Override
    public void shutDown() {
    }

    @Override
    protected void onInitialDaoBlockParsingComplete() {
    }

    @Override
    protected void startRequestBlocks() {
    }
}
