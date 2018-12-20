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

package bisq.core.dao.state.model;

import bisq.core.dao.state.model.blockchain.SpentInfo;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxOutputKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;

public class UnconfirmedState {

    @Getter
    private final Map<TxOutputKey, TxOutput> unspentTxOutputMap = new HashMap<>();
    @Getter
    private final Map<TxOutputKey, TxOutput> nonBsqTxOutputMap = new HashMap<>();
    @Getter
    private final Map<TxOutputKey, SpentInfo> spentInfoMap = new HashMap<>();
    @Getter
    private final List<String> parsedTxList = new ArrayList<>();

    public UnconfirmedState() {
    }

    public void reset() {
        unspentTxOutputMap.clear();
        nonBsqTxOutputMap.clear();
        spentInfoMap.clear();
        parsedTxList.clear();
    }
}
