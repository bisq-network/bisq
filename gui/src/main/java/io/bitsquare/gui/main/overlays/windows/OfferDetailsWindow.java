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

package io.bitsquare.gui.main.overlays.windows;

import com.google.common.base.Joiner;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.common.util.Tuple3;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.components.BusyAnimation;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.account.AccountView;
import io.bitsquare.gui.main.account.content.arbitratorselection.ArbitratorSelectionView;
import io.bitsquare.gui.main.account.settings.AccountSettingsView;
import io.bitsquare.gui.main.overlays.Overlay;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.locale.BSResources;
import io.bitsquare.locale.BankUtil;
import io.bitsquare.locale.CountryUtil;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.payment.PaymentMethod;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.user.Preferences;
import io.bitsquare.user.User;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;

import static io.bitsquare.gui.util.FormBuilder.*;

public class OfferDetailsWindow extends Overlay<OfferDetailsWindow> {
    protected static final Logger log = LoggerFactory.getLogger(OfferDetailsWindow.class);

    private final BSFormatter formatter;
    protected final Preferences preferences;
    private final User user;
    private final KeyRing keyRing;
    private final Navigation navigation;
    private Offer offer;
    private Coin tradeAmount;
    private Fiat tradePrice;
    private Optional<Runnable> placeOfferHandlerOptional = Optional.empty();
    private Optional<Runnable> takeOfferHandlerOptional = Optional.empty();
    private BusyAnimation busyAnimation;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public OfferDetailsWindow(BSFormatter formatter, Preferences preferences, User user, KeyRing keyRing, Navigation navigation) {
        this.formatter = formatter;
        this.preferences = preferences;
        this.user = user;
        this.keyRing = keyRing;
        this.navigation = navigation;
        type = Type.Confirmation;
    }

    public void show(Offer offer, Coin tradeAmount, Fiat tradePrice) {
        this.offer = offer;
        this.tradeAmount = tradeAmount;
        this.tradePrice = tradePrice;

        rowIndex = -1;
        width = 950;
        createGridPane();
        addContent();
        display();
    }

    public void show(Offer offer) {
        this.offer = offer;
        rowIndex = -1;
        width = 950;
        createGridPane();
        addContent();
        display();
    }

    public OfferDetailsWindow onPlaceOffer(Runnable placeOfferHandler) {
        this.placeOfferHandlerOptional = Optional.of(placeOfferHandler);
        return this;
    }

    public OfferDetailsWindow onTakeOffer(Runnable takeOfferHandler) {
        this.takeOfferHandlerOptional = Optional.of(takeOfferHandler);
        return this;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onHidden() {
        if (busyAnimation != null)
            busyAnimation.stop();
    }

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

        List<String> acceptedBanks = offer.getAcceptedBankIds();
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

        String fiatDirectionInfo = ":";
        String btcDirectionInfo = ":";
        Offer.Direction direction = offer.getDirection();
        String currencyCode = offer.getCurrencyCode();
        if (takeOfferHandlerOptional.isPresent()) {
            addLabelTextField(gridPane, rowIndex, "Offer type:", formatter.getDirectionForTakeOffer(direction, currencyCode), Layout.FIRST_ROW_DISTANCE);
            fiatDirectionInfo = direction == Offer.Direction.BUY ? " to receive:" : " to spend:";
            btcDirectionInfo = direction == Offer.Direction.SELL ? " to receive:" : " to spend:";
        } else if (placeOfferHandlerOptional.isPresent()) {
            addLabelTextField(gridPane, rowIndex, "Offer type:", formatter.getOfferDirectionForCreateOffer(direction, currencyCode), Layout.FIRST_ROW_DISTANCE);
            fiatDirectionInfo = direction == Offer.Direction.SELL ? " to receive:" : " to spend:";
            btcDirectionInfo = direction == Offer.Direction.BUY ? " to receive:" : " to spend:";
        } else {
            addLabelTextField(gridPane, rowIndex, "Offer type:", formatter.getDirectionBothSides(direction, currencyCode), Layout.FIRST_ROW_DISTANCE);
        }
        if (takeOfferHandlerOptional.isPresent()) {
            addLabelTextField(gridPane, ++rowIndex, "Bitcoin amount" + btcDirectionInfo, formatter.formatCoinWithCode(tradeAmount));
            addLabelTextField(gridPane, ++rowIndex, formatter.formatVolumeLabel(currencyCode) + fiatDirectionInfo,
                    formatter.formatVolumeWithCode(offer.getVolumeByAmount(tradeAmount)));
        } else {
            addLabelTextField(gridPane, ++rowIndex, "Bitcoin amount" + btcDirectionInfo, formatter.formatCoinWithCode(offer.getAmount()));
            addLabelTextField(gridPane, ++rowIndex, "Min. bitcoin amount:", formatter.formatCoinWithCode(offer.getMinAmount()));
            String volume = formatter.formatVolumeWithCode(offer.getOfferVolume());
            String minVolume = "";
            if (!offer.getAmount().equals(offer.getMinAmount()))
                minVolume = " (min. " + formatter.formatVolumeWithCode(offer.getMinOfferVolume()) + ")";
            addLabelTextField(gridPane, ++rowIndex, formatter.formatVolumeLabel(currencyCode) + fiatDirectionInfo, volume + minVolume);
        }

        if (takeOfferHandlerOptional.isPresent()) {
            addLabelTextField(gridPane, ++rowIndex, "Price:", formatter.formatPrice(tradePrice));
        } else {
            Fiat price = offer.getPrice();
            if (offer.getUseMarketBasedPrice()) {
                addLabelTextField(gridPane, ++rowIndex, "Price:", formatter.formatPrice(price) +
                        " (distance from market price: " + formatter.formatPercentagePrice(offer.getMarketPriceMargin()) + ")");
            } else {
                addLabelTextField(gridPane, ++rowIndex, "Price:", formatter.formatPrice(price));
            }
        }
        final PaymentMethod paymentMethod = offer.getPaymentMethod();
        final String offererPaymentAccountId = offer.getOffererPaymentAccountId();
        final PaymentAccount paymentAccount = user.getPaymentAccount(offererPaymentAccountId);
        String bankId = offer.getBankId();
        if (bankId == null || bankId.equals("null"))
            bankId = "";
        else
            bankId = " (" + bankId + ")";
        final boolean isSpecificBanks = paymentMethod.equals(PaymentMethod.SPECIFIC_BANKS);
        final boolean isNationalBanks = paymentMethod.equals(PaymentMethod.NATIONAL_BANK);
        final boolean isSepa = paymentMethod.equals(PaymentMethod.SEPA);
        if (offer.isMyOffer(keyRing) && offererPaymentAccountId != null && paymentAccount != null) {
            addLabelTextField(gridPane, ++rowIndex, "Payment account:", paymentAccount.getAccountName());
        } else {
            final String method = BSResources.get(paymentMethod.getId());
            if (isNationalBanks || isSpecificBanks || isSepa) {
                if (BankUtil.isBankIdRequired(offer.getCountryCode()))
                    addLabelTextField(gridPane, ++rowIndex, "Payment method (offerers bank ID):", method + bankId);
                else if (BankUtil.isBankNameRequired(offer.getCountryCode()))
                    addLabelTextField(gridPane, ++rowIndex, "Payment method (offerers bank name):", method + bankId);
            } else {
                addLabelTextField(gridPane, ++rowIndex, "Payment method:", method);
            }
        }
        if (showAcceptedBanks) {
            if (paymentMethod.equals(PaymentMethod.SAME_BANK)) {
                addLabelTextField(gridPane, ++rowIndex, "Bank ID (e.g. BIC or SWIFT):", acceptedBanks.get(0));
            } else if (isSpecificBanks) {
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
                if (acceptedCountryCodes.size() == 1) {
                    countries = CountryUtil.getNameAndCode(acceptedCountryCodes.get(0));
                    tooltip = new Tooltip(countries);
                } else {
                    countries = CountryUtil.getCodesString(acceptedCountryCodes);
                    tooltip = new Tooltip(CountryUtil.getNamesByCodesString(acceptedCountryCodes));
                }
            }
            TextField acceptedCountries = addLabelTextField(gridPane, ++rowIndex, "Accepted taker countries:", countries).second;
            if (tooltip != null) {
                acceptedCountries.setMouseTransparent(false);
                acceptedCountries.setTooltip(tooltip);
            }
        }

        rows = 5;
        String paymentMethodCountryCode = offer.getCountryCode();
        if (paymentMethodCountryCode != null)
            rows++;
        if (offer.getOfferFeePaymentTxID() != null)
            rows++;

        addTitledGroupBg(gridPane, ++rowIndex, rows, "Details", Layout.GROUP_DISTANCE);
        addLabelTextFieldWithCopyIcon(gridPane, rowIndex, "Offer ID:", offer.getId(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, "Offerers onion address:", offer.getOffererNodeAddress().getFullAddress());
        addLabelTextField(gridPane, ++rowIndex, "Creation date:", formatter.formatDateTime(offer.getDate()));
        addLabelTextField(gridPane, ++rowIndex, "Security deposit:", formatter.formatCoinWithCode(FeePolicy.getSecurityDeposit()));

        if (paymentMethodCountryCode != null)
            addLabelTextField(gridPane, ++rowIndex, "Offerers country of bank:",
                    CountryUtil.getNameAndCode(paymentMethodCountryCode));

        addLabelTextFieldWithCopyIcon(gridPane, ++rowIndex, "Accepted arbitrators:", formatter.arbitratorAddressesToString(offer.getArbitratorNodeAddresses()));
        if (offer.getOfferFeePaymentTxID() != null)
            addLabelTxIdTextField(gridPane, ++rowIndex, "Offer fee transaction ID:", offer.getOfferFeePaymentTxID());

        if (placeOfferHandlerOptional.isPresent()) {
            addTitledGroupBg(gridPane, ++rowIndex, 1, "Commitment", Layout.GROUP_DISTANCE);
            addLabelTextField(gridPane, rowIndex, "I agree:", Offer.TAC_OFFERER, Layout.FIRST_ROW_AND_GROUP_DISTANCE);

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

    private void addConfirmAndCancelButtons(boolean isPlaceOffer) {
        boolean isBuyOffer = offer.getDirection() == Offer.Direction.BUY;
        boolean isBuyerRole = isPlaceOffer ? isBuyOffer : !isBuyOffer;

        // String placeOfferButtonText = isBuyerRole ? "Confirm offer for buying bitcoin" : "Confirm offer for selling bitcoin";
        // String takeOfferButtonText = isBuyerRole ? "Confirm offer for buying bitcoin" : "Confirm offer for selling bitcoin";
        String placeOfferButtonText = "Confirm";
        String takeOfferButtonText = "Confirm";

        ImageView iconView = new ImageView();
        iconView.setId(isBuyerRole ? "image-buy-white" : "image-sell-white");

        Tuple3<Button, BusyAnimation, Label> placeOfferTuple = addButtonBusyAnimationLabelAfterGroup(gridPane, ++rowIndex, isPlaceOffer ? placeOfferButtonText : takeOfferButtonText);

        Button button = placeOfferTuple.first;
        button.setMinHeight(40);
        button.setPadding(new Insets(0, 20, 0, 20));
        button.setGraphic(iconView);
        button.setGraphicTextGap(10);
        button.setId(isBuyerRole ? "buy-button-big" : "sell-button-big");
        button.setText(isPlaceOffer ? placeOfferButtonText : takeOfferButtonText);

        busyAnimation = placeOfferTuple.second;
        Label spinnerInfoLabel = placeOfferTuple.third;

        Button cancelButton = addButton(gridPane, ++rowIndex, BSResources.get("shared.cancel"));
        cancelButton.setDefaultButton(false);
        cancelButton.setId("cancel-button");
        cancelButton.setOnAction(e -> {
            closeHandlerOptional.ifPresent(Runnable::run);
            hide();
        });

        button.setOnAction(e -> {
            if (user.getAcceptedArbitrators().size() > 0) {
                button.setDisable(true);
                cancelButton.setDisable(true);
                busyAnimation.play();
                if (isPlaceOffer) {
                    spinnerInfoLabel.setText(BSResources.get("createOffer.fundsBox.placeOfferSpinnerInfo"));
                    placeOfferHandlerOptional.get().run();
                } else {
                    spinnerInfoLabel.setText(BSResources.get("takeOffer.fundsBox.takeOfferSpinnerInfo"));
                    takeOfferHandlerOptional.get().run();
                }
            } else {
                new Popup().warning("You have no arbitrator selected.\n" +
                        "Please select at least one arbitrator.").show();

                navigation.navigateTo(MainView.class, AccountView.class, AccountSettingsView.class, ArbitratorSelectionView.class);
            }
        });
    }
}
