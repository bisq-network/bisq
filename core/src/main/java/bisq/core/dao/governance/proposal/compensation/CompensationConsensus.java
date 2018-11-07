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

package bisq.core.dao.governance.proposal.compensation;

import bisq.core.dao.governance.param.Param;
import bisq.core.dao.state.DaoStateService;

import org.bitcoinj.core.Coin;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CompensationConsensus {
    public static Coin getMinCompensationRequestAmount(DaoStateService daoStateService, int chainHeight) {
        return daoStateService.getParamValueAsCoin(Param.COMPENSATION_REQUEST_MIN_AMOUNT, chainHeight);
    }

    public static Coin getMaxCompensationRequestAmount(DaoStateService daoStateService, int chainHeight) {
        return daoStateService.getParamValueAsCoin(Param.COMPENSATION_REQUEST_MAX_AMOUNT, chainHeight);
    }

}
