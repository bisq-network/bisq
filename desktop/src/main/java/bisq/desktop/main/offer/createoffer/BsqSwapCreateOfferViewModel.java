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

package bisq.desktop.main.offer.createoffer;

import bisq.desktop.common.model.ViewModel;
import bisq.desktop.main.offer.BsqSwapOfferViewModel;
import bisq.desktop.util.validation.AltcoinValidator;
import bisq.desktop.util.validation.BsqValidator;
import bisq.desktop.util.validation.BtcValidator;
import bisq.desktop.util.validation.FiatPriceValidator;
import bisq.desktop.util.validation.FiatVolumeValidator;
import bisq.desktop.util.validation.SecurityDepositValidator;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.offer.OfferUtil;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;

import com.google.inject.Inject;

import javax.inject.Named;

class BsqSwapCreateOfferViewModel extends BsqSwapOfferViewModel<BsqSwapCreateOfferDataModel> implements ViewModel {

    @Inject
    public BsqSwapCreateOfferViewModel(BsqSwapCreateOfferDataModel dataModel,
                                       FiatVolumeValidator fiatVolumeValidator,
                                       FiatPriceValidator fiatPriceValidator,
                                       AltcoinValidator altcoinValidator,
                                       BtcValidator btcValidator,
                                       BsqValidator bsqValidator,
                                       SecurityDepositValidator securityDepositValidator,
                                       AccountAgeWitnessService accountAgeWitnessService,
                                       @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter,
                                       BsqFormatter bsqFormatter,
                                       OfferUtil offerUtil) {
        super(dataModel,
                fiatPriceValidator,
                altcoinValidator,
                btcValidator,
                bsqValidator,
                securityDepositValidator,
                accountAgeWitnessService,
                btcFormatter,
                bsqFormatter,
                offerUtil);
    }
}
