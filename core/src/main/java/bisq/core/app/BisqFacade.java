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

package bisq.core.app;

import bisq.core.btc.Balances;
import bisq.core.presentation.BalancePresentation;

import bisq.common.app.Version;

import javax.inject.Inject;

/**
 * Provides high level interface to functionality of core Bisq features.
 * E.g. useful for different APIs to access data of different domains of Bisq.
 */
public class BisqFacade {
    private final Balances balances;
    private final BalancePresentation balancePresentation;

    @Inject
    public BisqFacade(Balances balances, BalancePresentation balancePresentation) {
        this.balances = balances;
        this.balancePresentation = balancePresentation;
    }

    public String getVersion() {
        return Version.VERSION;
    }

    public long getAvailableBalance() {
        return balances.getAvailableBalance().get().getValue();
    }

    public String getAvailableBalanceAsString() {
        return balancePresentation.getAvailableBalance().get();
    }
}
