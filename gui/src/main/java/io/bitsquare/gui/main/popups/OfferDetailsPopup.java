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

import com.google.common.base.Joiner;
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
import io.bitsquare.payment.PaymentMethod;
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
import java.util.List;
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

        List<String> acceptedBanks = offer.getAcceptedBanks();
        boolean showAcceptedBanks = acceptedBanks != null && !acceptedBanks.isEmpty();
        List<String> acceptedCountryCodes = offer.getAcceptedCountryCodes();
        boolean showAcceptedCountryCodes = acceptedCountryCodes != null && !acceptedCountryCodes.isEmpty();

        if (!takeOfferHandlerOptional.isPresent())
            rows++;
        if (showAcceptedBanks)
            rows++;
        if (showAcceptedCountryCodes)
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

        if (showAcceptedBanks) {
            if (offer.getPaymentMethod().equals(PaymentMethod.SAME_BANK)) {
                addLabelTextField(gridPane, ++rowIndex, "Bank name:", acceptedBanks.get(0));
            } else if (offer.getPaymentMethod().equals(PaymentMethod.SPECIFIC_BANKS)) {
                String value = Joiner.on(", ").join(acceptedBanks);
                Tooltip tooltip = new Tooltip("Accepted banks: " + value);
                TextField acceptedBanksTextField = addLabelTextField(gridPane, ++rowIndex, "Accepted banks:", value).second;
                acceptedBanksTextField.setMouseTransparent(false);
                acceptedBanksTextField.setTooltip(tooltip);
            }
        }

        if (showAcceptedCountryCodes) {
            String countries;
            Tooltip tooltip = null;
            if (CountryUtil.containsAllSepaEuroCountries(acceptedCountryCodes)) {
                countries = "All Euro countries";
            } else {
                countries = CountryUtil.getCodesString(acceptedCountryCodes);
                tooltip = new Tooltip(CountryUtil.getNamesByCodesString(acceptedCountryCodes));
            }
            TextField acceptedCountries = addLabelTextField(gridPane, ++rowIndex, "Accepted taker countries:", countries).second;
            if (tooltip != null) {
                acceptedCountries.setMouseTransparent(false);
                acceptedCountries.setTooltip(tooltip);
            }
        }

        rows = 3;
        String paymentMethodCountryCode = offer.getPaymentMethodCountryCode();
        if (paymentMethodCountryCode != null)
            rows++;
        if (offer.getOfferFeePaymentTxID() != null)
            rows++;

        addTitledGroupBg(gridPane, ++rowIndex, rows, "Details", Layout.GROUP_DISTANCE);
        addLabelTextField(gridPane, rowIndex, "Offer ID:", offer.getId(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(gridPane, ++rowIndex, "Creation date:", formatter.formatDateTime(offer.getDate()));

        if (paymentMethodCountryCode != null)
            addLabelTextField(gridPane, ++rowIndex, "Offerers country of bank:",
                    CountryUtil.getNameAndCode(paymentMethodCountryCode));

        addLabelTextField(gridPane, ++rowIndex, "Accepted arbitrators:", formatter.arbitratorAddressesToString(offer.getArbitratorNodeAddresses()));
        if (offer.getOfferFeePaymentTxID() != null)
            addLabelTxIdTextField(gridPane, ++rowIndex, "Offer fee transaction ID:", offer.getOfferFeePaymentTxID());

        if (placeOfferHandlerOptional.isPresent()) {
            addTitledGroupBg(gridPane, ++rowIndex, 1, "Commitment", Layout.GROUP_DISTANCE);
            addLabelTextField(gridPane, rowIndex, "Please note:", Offer.TAC_OFFERER, Layout.FIRST_ROW_AND_GROUP_DISTANCE);

            addConfirmAndCancelButtons(true);
        } else if (takeOfferHandlerOptional.isPresent()) {
            addTitledGroupBg(gridPane, ++rowIndex, 1, "Contract", Layout.GROUP_DISTANCE);
            addLabelTextField(gridPane, rowIndex, "Terms and conditions:", Offer.TAC_TAKER, Layout.FIRST_ROW_AND_GROUP_DISTANCE);

            addConfirmAndCancelButtons(false);
        } else {
            Button cancelButton = addButtonAfterGroup(gridPane, ++rowIndex, "Close");
            cancelButton.setOnAction(e -> {
                closeHandlerOptional.ifPresent(Runnable::run);
                hide();
            });
        }
    }

    @NotNull
    private void addConfirmAndCancelButtons(boolean isPlaceOffer) {
        Tuple2<Button, Button> tuple = add2ButtonsAfterGroup(gridPane,
                ++rowIndex,
                isPlaceOffer ? "Confirm place offer" : "Confirm take offer",
                "Cancel");
        Button placeButton = tuple.first;

        boolean isBuyOffer = offer.getDirection() == Offer.Direction.BUY;
        boolean isBuyStyle = isPlaceOffer ? isBuyOffer : !isBuyOffer;
        placeButton.setId(isBuyStyle ? "buy-button" : "sell-button");

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

        Button cancelButton = tuple.second;
        cancelButton.setId("cancel-button");
        cancelButton.setOnAction(e -> {
            closeHandlerOptional.ifPresent(Runnable::run);
            hide();
        });
    }
}
