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

package bisq.desktop.main.dao.burnbsq.assetfee;

import bisq.core.dao.governance.asset.StatefulAsset;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import lombok.Value;

@Value
class AssetListItem {
    private final StatefulAsset statefulAsset;
    private final String tickerSymbol;
    private final String assetStateString;
    private final String activeFeeAsString;
    private final int trialPeriodInBlocks;
    private final String nameAndCode;
    private final long activeFee;
    private final String tradedVolumeAsString;

    AssetListItem(StatefulAsset statefulAsset,
                  BsqFormatter bsqFormatter) {
        this.statefulAsset = statefulAsset;

        tickerSymbol = statefulAsset.getTickerSymbol();
        nameAndCode = CurrencyUtil.getNameAndCode(tickerSymbol);
        assetStateString = Res.get("dao.assetState." + statefulAsset.getAssetState());
        activeFee = statefulAsset.getActiveFee();
        activeFeeAsString = Long.toString(activeFee);
        trialPeriodInBlocks = (int) activeFee * 144;
        tradedVolumeAsString = "TODO";
    }
}
