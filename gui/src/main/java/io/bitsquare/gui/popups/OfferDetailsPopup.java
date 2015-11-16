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

package io.bitsquare.gui.popups;

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
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Optional;
import java.util.function.Consumer;

import static io.bitsquare.gui.util.FormBuilder.*;

public class OfferDetailsPopup extends Popup {
    protected static final Logger log = LoggerFactory.getLogger(OfferDetailsPopup.class);

    private final BSFormatter formatter;
    private final Preferences preferences;
    private User user;
    private final Navigation navigation;
    private Offer offer;
    private Optional<Consumer<Offer>> placeOfferHandlerOptional = Optional.empty();
    private Optional<Runnable> takeOfferHandlerOptional = Optional.empty();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public OfferDetailsPopup(BSFormatter formatter, Preferences preferences, User user, Navigation navigation) {
        this.formatter = formatter;
        this.preferences = preferences;
        this.user = user;
        this.navigation = navigation;
    }

    public OfferDetailsPopup show(Offer offer) {
        this.offer = offer;

        rowIndex = -1;
        width = 850;
        createGridPane();
        addContent();
        createPopup();
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
        int rows = 11;
        if (offer.getPaymentMethodCountryCode() != null)
            rows++;
        if (offer.getOfferFeePaymentTxID() != null)
            rows++;
        if (offer.getAcceptedCountryCodes() != null)
            rows++;
        if (placeOfferHandlerOptional.isPresent())
            rows -= 2;

        addTitledGroupBg(gridPane, ++rowIndex, rows, "Offer details");
        addLabelTextField(gridPane, rowIndex, "Offer ID:", offer.getId(), Layout.FIRST_ROW_DISTANCE);
        addLabelTextField(gridPane, ++rowIndex, "Creation date:", formatter.formatDateTime(offer.getDate()));
        addLabelTextField(gridPane, ++rowIndex, "Offer direction:", Offer.Direction.BUY.name());
        addLabelTextField(gridPane, ++rowIndex, "Currency:", offer.getCurrencyCode());
        addLabelTextField(gridPane, ++rowIndex, "Price:", formatter.formatFiat(offer.getPrice()) + " " + offer.getCurrencyCode() + "/" + "BTC");
        addLabelTextField(gridPane, ++rowIndex, "Amount:", formatter.formatCoinWithCode(offer.getAmount()));
        addLabelTextField(gridPane, ++rowIndex, "Min. amount:", formatter.formatCoinWithCode(offer.getMinAmount()));
        addLabelTextField(gridPane, ++rowIndex, "Payment method:", BSResources.get(offer.getPaymentMethod().getId()));
        if (offer.getPaymentMethodCountryCode() != null)
            addLabelTextField(gridPane, ++rowIndex, "Offerers country of bank:", offer.getPaymentMethodCountryCode());
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
            if (tooltip != null) acceptedCountries.setTooltip(new Tooltip());
        }
        addLabelTextField(gridPane, ++rowIndex, "Accepted arbitrators:", formatter.arbitratorAddressesToString(offer.getArbitratorAddresses()));
        if (offer.getOfferFeePaymentTxID() != null)
            addLabelTxIdTextField(gridPane, ++rowIndex, "Create offer fee transaction ID:", offer.getOfferFeePaymentTxID());

        if (placeOfferHandlerOptional.isPresent()) {
            Tuple2<Button, Button> tuple = add2ButtonsAfterGroup(gridPane, ++rowIndex, "Confirm place offer", "Cancel");
            Button placeButton = tuple.first;
            placeButton.setOnAction(e -> {
                if (user.getAcceptedArbitrators().size() > 0) {
                    placeOfferHandlerOptional.get().accept(offer);
                } else {
                    new Popup().warning("You have no arbitrator selected.\n" +
                            "Please select at least one arbitrator.").show();

                    navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class, ArbitratorSelectionView.class);
                }
                hide();
            });

            Button cancelButton = tuple.second;
            cancelButton.setOnAction(e -> {
                closeHandlerOptional.ifPresent(closeHandler -> closeHandler.run());
                hide();
            });

            CheckBox checkBox = addCheckBox(gridPane, ++rowIndex, "Don't show again", 5);
            checkBox.setSelected(!preferences.getShowPlaceOfferConfirmation());
            checkBox.setOnAction(e -> preferences.setShowPlaceOfferConfirmation(!checkBox.isSelected()));
        } else if (takeOfferHandlerOptional.isPresent()) {
            Tuple2<Button, Button> tuple = add2ButtonsAfterGroup(gridPane, ++rowIndex, "Confirm take offer", "Cancel");
            Button placeButton = tuple.first;
            placeButton.setOnAction(e -> {
                if (user.getAcceptedArbitrators().size() > 0) {
                    takeOfferHandlerOptional.get().run();
                } else {
                    new Popup().warning("You have no arbitrator selected.\n" +
                            "Please select at least one arbitrator.").show();

                    navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class, ArbitratorSelectionView.class);
                }
                hide();
            });

            Button cancelButton = tuple.second;
            cancelButton.setOnAction(e -> {
                closeHandlerOptional.ifPresent(closeHandler -> closeHandler.run());
                hide();
            });

            CheckBox checkBox = addCheckBox(gridPane, ++rowIndex, "Don't show again", 5);
            checkBox.setPadding(new Insets(10, 0, 15, 0));
            checkBox.setSelected(!preferences.getShowTakeOfferConfirmation());
            checkBox.setOnAction(e -> preferences.setShowTakeOfferConfirmation(!checkBox.isSelected()));
        } else {
            Button cancelButton = addButtonAfterGroup(gridPane, ++rowIndex, "Close");
            cancelButton.setOnAction(e -> {
                closeHandlerOptional.ifPresent(closeHandler -> closeHandler.run());
                hide();
            });
        }
    }
}
