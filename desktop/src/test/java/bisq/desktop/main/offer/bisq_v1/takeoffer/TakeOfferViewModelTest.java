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

package bisq.desktop.main.offer.bisq_v1.takeoffer;

import bisq.desktop.Navigation;
import bisq.desktop.util.validation.BtcValidator;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.Res;
import bisq.core.offer.OfferUtil;
import bisq.core.user.Preferences;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.coin.ImmutableCoinFormatter;

import bisq.network.p2p.P2PService;

import bisq.common.config.Config;
import bisq.common.handlers.ErrorMessageHandler;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TakeOfferViewModelTest {
    // A refusal before a trade exists, for example of the trade tx fee, ends the attempt like an offer that is not
    // available: the view hides the confirmation window, shows the warning and closes the screen.
    @Test
    void refusalBeforeATradeExistsSetsTheOfferWarning() {
        Res.setup();
        CoinFormatter coinFormatter = new ImmutableCoinFormatter(
                Config.baseCurrencyNetworkParameters().getMonetaryFormat());
        TakeOfferDataModel dataModel = mock(TakeOfferDataModel.class);
        TakeOfferViewModel model = new TakeOfferViewModel(dataModel,
                mock(OfferUtil.class),
                new BtcValidator(coinFormatter),
                mock(P2PService.class),
                mock(AccountAgeWitnessService.class),
                mock(Navigation.class),
                mock(Preferences.class),
                coinFormatter,
                mock(BsqFormatter.class));

        model.onTakeOffer(() -> {
        });
        ArgumentCaptor<ErrorMessageHandler> refusalHandler = ArgumentCaptor.forClass(ErrorMessageHandler.class);
        verify(dataModel).onTakeOffer(any(), refusalHandler.capture());
        assertNull(model.offerWarning.get());

        refusalHandler.getValue().handleErrorMessage("refused");

        assertEquals("refused", model.offerWarning.get());
    }
}
