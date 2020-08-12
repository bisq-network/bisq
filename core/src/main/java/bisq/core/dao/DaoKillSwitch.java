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

import bisq.common.app.Version;

import javax.inject.Inject;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public class DaoKillSwitch implements DaoSetupService {
    private final FilterManager filterManager;

    @Getter
    private boolean daoDisabled;

    @Inject
    public DaoKillSwitch(FilterManager filterManager) {
        this.filterManager = filterManager;
    }

    @Override
    public void addListeners() {
        filterManager.filterProperty().addListener((observable, oldValue, newValue) -> applyFilter(newValue));
    }

    @Override
    public void start() {
        applyFilter(filterManager.getFilter());
    }

    private void applyFilter(@Nullable Filter filter) {
        if (filter == null) {
            daoDisabled = false;
            return;
        }

        boolean requireUpdateToNewVersion = false;
        String disableDaoBelowVersion = filter.getDisableDaoBelowVersion();
        if (disableDaoBelowVersion != null && !disableDaoBelowVersion.isEmpty()) {
            requireUpdateToNewVersion = Version.isNewVersion(disableDaoBelowVersion);
        }

        daoDisabled = requireUpdateToNewVersion || filter.isDisableDao();
    }

    public void assertDaoIsNotDisabled() {
        if (isDaoDisabled()) {
            throw new DaoDisabledException("The DAO features have been disabled by the Bisq developers. " +
                    "Please check out the Bisq Forum for further information.");
        }
    }
}
