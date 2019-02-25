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

package bisq.core.dao;

import bisq.core.dao.exceptions.DaoDisabledException;
import bisq.core.filter.Filter;
import bisq.core.filter.FilterManager;

import javax.inject.Inject;

import lombok.Getter;

public class DaoKillSwitch implements DaoSetupService {
    private static DaoKillSwitch INSTANCE;
    private final FilterManager filterManager;

    @Getter
    private boolean daoDisabled;

    @Inject
    public DaoKillSwitch(FilterManager filterManager) {
        this.filterManager = filterManager;

        DaoKillSwitch.INSTANCE = this;
    }

    @Override
    public void addListeners() {
        filterManager.filterProperty().addListener((observable, oldValue, newValue) -> applyFilter(newValue));
    }

    @Override
    public void start() {
        applyFilter(filterManager.getFilter());
    }

    private void applyFilter(Filter filter) {
        daoDisabled = filter != null && filter.isDisableDao();
    }

    public static void assertDaoIsNotDisabled() {
        if (INSTANCE.isDaoDisabled()) {
            throw new DaoDisabledException("The DAO features have been disabled by the Bisq developers. " +
                    "Please check out the Bisq Forum for further information.");
        }
    }
}
