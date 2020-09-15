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

package bisq.core.support.dispute.arbitration;

import bisq.core.support.dispute.DisputeListService;

import bisq.common.persistence.PersistenceManager;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class ArbitrationDisputeListService extends DisputeListService<ArbitrationDisputeList> {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ArbitrationDisputeListService(PersistenceManager<ArbitrationDisputeList> persistenceManager) {
        super(persistenceManager);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Implement template methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected ArbitrationDisputeList getConcreteDisputeList() {
        return new ArbitrationDisputeList();
    }

    @Override
    protected String getFileName() {
        return "DisputeList";
    }
}
