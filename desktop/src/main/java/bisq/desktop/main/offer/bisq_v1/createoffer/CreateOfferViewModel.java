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

package bisq.desktop.main.offer.bisq_v1.createoffer;

import bisq.desktop.Navigation;
import bisq.desktop.common.model.ViewModel;
import bisq.desktop.main.offer.bisq_v1.MutableOfferViewModel;
import bisq.desktop.util.validation.BsqValidator;
import bisq.desktop.util.validation.BtcValidator;
import bisq.desktop.util.validation.FiatVolumeValidator;
import bisq.desktop.util.validation.SecurityDepositValidator;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.offer.OfferUtil;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.Preferences;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.AltcoinValidator;
import bisq.core.util.validation.FiatPriceValidator;

import com.google.inject.Inject;

import javax.inject.Named;

class CreateOfferViewModel extends MutableOfferViewModel<CreateOfferDataModel> implements ViewModel {

    @Inject
    public CreateOfferViewModel(CreateOfferDataModel dataModel,
                                FiatVolumeValidator fiatVolumeValidator,
                                FiatPriceValidator fiatPriceValidator,
                                AltcoinValidator altcoinValidator,
                                BtcValidator btcValidator,
                                BsqValidator bsqValidator,
                                SecurityDepositValidator securityDepositValidator,
                                PriceFeedService priceFeedService,
                                AccountAgeWitnessService accountAgeWitnessService,
                                Navigation navigation,
                                Preferences preferences,
                                @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter,
                                BsqFormatter bsqFormatter,
                                OfferUtil offerUtil) {
        super(dataModel,
                fiatVolumeValidator,
                fiatPriceValidator,
                altcoinValidator,
                btcValidator,
                bsqValidator,
                securityDepositValidator,
                priceFeedService,
                accountAgeWitnessService,
                navigation,
                preferences,
                btcFormatter,
                bsqFormatter,
                offerUtil);
    }
}
