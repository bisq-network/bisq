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

package bisq.core.util;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.governance.param.Param;
import bisq.core.filter.Filter;
import bisq.core.filter.FilterManager;

import java.util.List;
import java.util.Random;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FeeReceiverSelector {
    public static String getAddress(DaoFacade daoFacade, FilterManager filterManager) {
        // We keep default value as fallback in case no filter value is available or user has old version.
        String feeReceiver = daoFacade.getParamValue(Param.RECIPIENT_BTC_ADDRESS);

        Filter filter = filterManager.getFilter();
        if (filter != null) {
            List<String> feeReceivers = filter.getBtcFeeReceiverAddresses();
            if (feeReceivers != null && !feeReceivers.isEmpty()) {
                int index = new Random().nextInt(feeReceivers.size());
                feeReceiver = feeReceivers.get(index);
            }
        }

        return feeReceiver;
    }
}
