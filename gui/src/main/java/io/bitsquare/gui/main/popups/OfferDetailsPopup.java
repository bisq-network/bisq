/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.main.popups;

import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.account.AccountView;
import io.bitsquare.gui.main.account.content.arbitratorselection.ArbitratorSelectionView;
import io.bitsquare.gui.main.account.settings.AccountSettingsView;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.locale.BSResources;
import io.bitsquare.locale.CountryUtil;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.user.Preferences;
import io.bitsquare.user.User;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import org.bitcoinj.core.Coin;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Optional;
import java.util.function.Consumer;

import static io.bitsquare.gui.util.FormBuilder.*;

public class OfferDetailsPopup extends Popup {
    protected static final Logger log = LoggerFactory.getLogger(OfferDetailsPopup.class);

    private final BSFormatter formatter;
    protected final Preferences preferences;
    private final User user;
    private final KeyRing keyRing;
    private final Navigation navigation;
    private Offer offer;
    private Coin tradeAmount;
    private Optional<Consumer<Offer>> placeOfferHandlerOptional = Optional.empty();
    private Optional<Runnable> takeOfferHandlerOptional = Optional.empty();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public OfferDetailsPopup(BSFormatter formatter, Preferences preferences, User user, KeyRing keyRing, Navigation navigation) {
        this.formatter = formatter;
        this.preferences = preferences;
        this.user = user;
        this.keyRing = keyRing;
        this.navigation = navigation;
    }

    public OfferDetailsPopup show(Offer offer, Coin tradeAmount) {
        this.offer = offer;
        this.tradeAmount = tradeAmount;

        rowIndex = -1;
        width = 850;
        createGridPane();
        addContent();
        display();
        return this;
    }

    public OfferDetailsPopup show(Offer offer) {
        this.offer = offer;

        rowIndex = -1;
        width = 850;
        createGridPane();
        addContent();
        display();
        return this;
    }

    public OfferDetailsPopup dontShowAgainId(String dontShowAgainId) {
        this.dontShowAgainId = dontShowAgainId;
        return this;
    }

    public OfferDetailsPopup onPlaceOffer(Consumer<Offer> placeOfferHandler) {
        this.placeOfferHandlerOptional = Optional.of(placeOfferHandler);
        return this;
    }

    public OfferDetailsPopup onTakeOffer(Runnable takeOfferHandler) {
        this.takeOfferHandlerOptional = Optional.of(takeOfferHandler);
        return this;
    }

    public OfferDetailsPopup onClose(Runnable closeHandler) {
        this.closeHandlerOptional = Optional.of(closeHandler);
        return this;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void createGridPane() {
        super.createGridPane();
        gridPane.setPadding(new Insets(35, 40, 30, 40));
        gridPane.setStyle("-fx-background-color: -bs-content-bg-grey;" +
                        "-fx-background-radius: 5 5 5 5;" +
                        "-fx-effect: dropshadow(gaussian, #999, 10, 0, 0, 0);" +
                        "-fx-background-insets: 10;"
        );
    }

    private void addContent() {
        int rows = 5;
        if (!takeOfferHandlerOptional.isPresent())
            rows++;

        addTitledGroupBg(gridPane, ++rowIndex, rows, "Offer");

        if (takeOfferHandlerOptional.isPresent())
            addLabelTextField(gridPane, rowIndex, "Offer type:", formatter.getDirectionForTaker(offer.getDirection()), Layout.FIRST_ROW_DISTANCE);
        else
            addLabelTextField(gridPane, rowIndex, "Offer type:", formatter.getOfferDirection(offer.getDirection()), Layout.FIRST_ROW_DISTANCE);

        if (takeOfferHandlerOptional.isPresent()) {
            addLabelTextField(gridPane, ++rowIndex, "Trade amount:", formatter.formatCoinWithCode(tradeAmount));
        } else {
            addLabelTextField(gridPane, ++rowIndex, "Amount:", formatter.formatCoinWithCode(offer.getAmount()));
            addLabelTextField(gridPane, ++rowIndex, "Min. amount:", formatter.formatCoinWithCode(offer.getMinAmount()));
        }

        addLabelTextField(gridPane, ++rowIndex, "Price:", formatter.formatFiat(offer.getPrice()) + " " + offer.getCurrencyCode() + "/" + "BTC");

        addLabelTextField(gridPane, ++rowIndex, "Currency:", offer.getCurrencyCode());
        
        if (offer.isMyOffer(keyRing) && user.getPaymentAccount(offer.getOffererPaymentAccountId()) != null)
            addLabelTextField(gridPane, ++rowIndex, "Payment account:", user.getPaymentAccount(offer.getOffererPaymentAccountId()).getAccountName());
        else
            addLabelTextField(gridPane, ++rowIndex, "Payment method:", BSResources.get(offer.getPaymentMethod().getId()));

        rows = 3;
        String paymentMethodCountryCode = offer.getPaymentMethodCountryCode();
        if (paymentMethodCountryCode != null)
            rows++;
        if (offer.getOfferFeePaymentTxID() != null)
            rows++;
        if (offer.getAcceptedCountryCodes() != null)
            rows++;
     /*   if (placeOfferHandlerOptional.isPresent())
            rows -= 2;*/

        addTitledGroupBg(gridPane, ++rowIndex, rows, "Details", Layout.GROUP_DISTANCE);
        addLabelTextField(gridPane, rowIndex, "Offer ID:", offer.getId(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(gridPane, ++rowIndex, "Creation date:", formatter.formatDateTime(offer.getDate()));

        if (paymentMethodCountryCode != null)
            addLabelTextField(gridPane, ++rowIndex, "Offerers country of bank:",
                    CountryUtil.getNameAndCode(paymentMethodCountryCode));
        if (offer.getAcceptedCountryCodes() != null) {
            String countries;
            Tooltip tooltip = null;
            if (CountryUtil.containsAllSepaEuroCountries(offer.getAcceptedCountryCodes())) {
                countries = "All Euro countries";
            } else {
                countries = CountryUtil.getCodesString(offer.getAcceptedCountryCodes());
                tooltip = new Tooltip(CountryUtil.getNamesByCodesString(offer.getAcceptedCountryCodes()));
            }
            TextField acceptedCountries = addLabelTextField(gridPane, ++rowIndex, "Accepted taker countries:", countries).second;
            if (tooltip != null) {
                acceptedCountries.setMouseTransparent(false);
                acceptedCountries.setTooltip(tooltip);
            }
        }
        addLabelTextField(gridPane, ++rowIndex, "Accepted arbitrators:", formatter.arbitratorAddressesToString(offer.getArbitratorNodeAddresses()));
        if (offer.getOfferFeePaymentTxID() != null)
            addLabelTxIdTextField(gridPane, ++rowIndex, "Offer fee transaction ID:", offer.getOfferFeePaymentTxID());

        if (placeOfferHandlerOptional.isPresent()) {
            addTitledGroupBg(gridPane, ++rowIndex, 1, "Commitment", Layout.GROUP_DISTANCE);
            addLabelTextField(gridPane, rowIndex, "Please note:", Offer.TAC_OFFERER, Layout.FIRST_ROW_AND_GROUP_DISTANCE);

            Button cancelButton = addConfirmButton(true);
            addCancelButton(cancelButton);
        } else if (takeOfferHandlerOptional.isPresent()) {
            addTitledGroupBg(gridPane, ++rowIndex, 1, "Contract", Layout.GROUP_DISTANCE);
            addLabelTextField(gridPane, rowIndex, "Terms and conditions:", Offer.TAC_TAKER, Layout.FIRST_ROW_AND_GROUP_DISTANCE);

            Button cancelButton = addConfirmButton(false);
            addCancelButton(cancelButton);
        } else {
            Button cancelButton = addButtonAfterGroup(gridPane, ++rowIndex, "Close");
            cancelButton.setOnAction(e -> {
                closeHandlerOptional.ifPresent(closeHandler -> closeHandler.run());
                hide();
            });
        }
    }

    @NotNull
    private Button addConfirmButton(boolean isPlaceOffer) {
        Tuple2<Button, Button> tuple = add2ButtonsAfterGroup(gridPane,
                ++rowIndex,
                isPlaceOffer ? "Confirm place offer" : "Confirm take offer",
                "Cancel");
        Button placeButton = tuple.first;
        placeButton.setOnAction(e -> {
            if (user.getAcceptedArbitrators().size() > 0) {
                if (isPlaceOffer)
                    placeOfferHandlerOptional.get().accept(offer);
                else
                    takeOfferHandlerOptional.get().run();
            } else {
                new Popup().warning("You have no arbitrator selected.\n" +
                        "Please select at least one arbitrator.").show();

                navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class, ArbitratorSelectionView.class);
            }
            hide();
        });
        return tuple.second;
    }

    private void addCancelButton(Button cancelButton) {
        cancelButton.setOnAction(e -> {
            closeHandlerOptional.ifPresent(closeHandler -> closeHandler.run());
            hide();
        });
    }
}
