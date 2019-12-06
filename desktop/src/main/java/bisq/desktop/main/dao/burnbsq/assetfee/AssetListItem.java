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
import bisq.core.locale.Res;
import bisq.core.util.coin.BsqFormatter;

import lombok.Value;

@Value
class AssetListItem {
    private final StatefulAsset statefulAsset;
    private final String tickerSymbol;
    private final String assetStateString;
    private final int trialPeriodInBlocks;
    private final String nameAndCode;
    private final long totalFeesPaid;
    private final String totalFeesPaidAsString;
    private final long feeOfTrialPeriod;
    private final String feeOfTrialPeriodAsString;
    private final String tradedVolumeAsString;
    private final String lookBackPeriodInDays;
    private final long tradedVolume;

    AssetListItem(StatefulAsset statefulAsset,
                  BsqFormatter bsqFormatter) {
        this.statefulAsset = statefulAsset;

        tickerSymbol = statefulAsset.getTickerSymbol();
        nameAndCode = statefulAsset.getNameAndCode();
        assetStateString = Res.get("dao.assetState." + statefulAsset.getAssetState());
        feeOfTrialPeriod = statefulAsset.getFeeOfTrialPeriod();
        feeOfTrialPeriodAsString = bsqFormatter.formatCoinWithCode(feeOfTrialPeriod);
        totalFeesPaid = statefulAsset.getTotalFeesPaid();
        totalFeesPaidAsString = bsqFormatter.formatCoinWithCode(totalFeesPaid);
        trialPeriodInBlocks = (int) totalFeesPaid * 144;
        tradedVolume = statefulAsset.getTradeVolume();
        tradedVolumeAsString = bsqFormatter.formatBTCWithCode(tradedVolume);
        lookBackPeriodInDays = Res.get("dao.burnBsq.assets.days", statefulAsset.getLookBackPeriodInDays());
    }
}
