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

package bisq.desktop.main.offer.offerbook;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.main.MainView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.BsqSwapOfferDetailsWindow;
import bisq.desktop.main.overlays.windows.OfferDetailsWindow;
import bisq.desktop.main.settings.SettingsView;
import bisq.desktop.main.settings.preferences.PreferencesView;

import bisq.core.account.sign.SignedWitnessService;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.alert.PrivateNotificationManager;
import bisq.core.locale.Res;
import bisq.core.offer.OfferDirection;
import bisq.core.user.Preferences;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;

import bisq.common.config.Config;

import javax.inject.Inject;
import javax.inject.Named;

import javafx.scene.layout.GridPane;

import javafx.beans.value.ChangeListener;

import java.math.BigDecimal;

@FxmlView
public class BsqOfferBookView extends OfferBookView<GridPane, BsqOfferBookViewModel> {
    private final Navigation navigation;
    private final Preferences preferences;
    private final ChangeListener<Boolean> allOffersOutsidePriceRangeListener;
    // At most one popup while the tab stays selected
    private boolean allOffersOutsidePriceRangePopupShown;

    @Inject
    BsqOfferBookView(BsqOfferBookViewModel model,
                     Navigation navigation,
                     OfferDetailsWindow offerDetailsWindow,
                     BsqSwapOfferDetailsWindow bsqSwapOfferDetailsWindow,
                     @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter formatter,
                     PrivateNotificationManager privateNotificationManager,
                     @Named(Config.USE_DEV_PRIVILEGE_KEYS) boolean useDevPrivilegeKeys,
                     AccountAgeWitnessService accountAgeWitnessService,
                     SignedWitnessService signedWitnessService,
                     Preferences preferences) {
        super(model, navigation, offerDetailsWindow, bsqSwapOfferDetailsWindow, formatter, privateNotificationManager, useDevPrivilegeKeys, accountAgeWitnessService, signedWitnessService);
        this.navigation = navigation;
        this.preferences = preferences;
        allOffersOutsidePriceRangeListener = (observable, oldValue, newValue) -> maybeShowAllOffersOutsidePriceRangePopup();
    }

    @Override
    protected String getMarketTitle() {
        return model.getDirection().equals(OfferDirection.BUY) ?
                Res.get("offerbook.availableOffersToBuy", BsqOfferBookViewModel.BSQ.getCode(), Res.getBaseCurrencyCode()) :
                Res.get("offerbook.availableOffersToSell", BsqOfferBookViewModel.BSQ.getCode(), Res.getBaseCurrencyCode());
    }

    @Override
    protected void activate() {
        model.onSetTradeCurrency(BsqOfferBookViewModel.BSQ);

        super.activate();

        currencyComboBoxContainer.setVisible(false);
        currencyComboBoxContainer.setManaged(false);

        model.allOffersOutsidePriceRange.addListener(allOffersOutsidePriceRangeListener);
        maybeShowAllOffersOutsidePriceRangePopup();
    }

    @Override
    protected void deactivate() {
        super.deactivate();

        model.allOffersOutsidePriceRange.removeListener(allOffersOutsidePriceRangeListener);
        allOffersOutsidePriceRangePopupShown = false;
    }

    @Override
    public void onTabSelected(boolean isSelected) {
        super.onTabSelected(isSelected);

        if (isSelected) {
            maybeShowAllOffersOutsidePriceRangePopup();
        } else {
            allOffersOutsidePriceRangePopupShown = false;
        }
    }

    @Override
    String getTradeCurrencyCode() {
        return BsqOfferBookViewModel.BSQ.getCode();
    }

    private void maybeShowAllOffersOutsidePriceRangePopup() {
        if (allOffersOutsidePriceRangePopupShown || !model.allOffersOutsidePriceRange.get()) {
            return;
        }

        allOffersOutsidePriceRangePopupShown = true;
        String factor = BigDecimal.valueOf(preferences.getBsqOfferPriceRangeFactor()).stripTrailingZeros().toPlainString();
        new Popup().information(Res.get("offerbook.bsq.allOffersOutsidePriceRange", factor))
                .actionButtonTextWithGoTo("navigation.settings.preferences")
                .onAction(() -> navigation.navigateTo(MainView.class, SettingsView.class, PreferencesView.class))
                .closeButtonText(Res.get("shared.close"))
                .dontShowAgainId("bsqOfferBookAllOffersOutsidePriceRange")
                .show();
    }
}
