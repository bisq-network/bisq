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

package bisq.desktop.main.overlays.windows;

import bisq.desktop.main.overlays.Overlay;

import bisq.core.locale.Res;
import bisq.core.locale.Country;
import bisq.core.payment.AmazonGiftCardAccount;
import bisq.core.user.User;

import bisq.common.UserThread;

import javafx.scene.Scene;
import javafx.scene.control.ComboBox;

import javafx.collections.FXCollections;

import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static bisq.core.locale.CountryUtil.findCountryByCode;
import static bisq.core.locale.CountryUtil.getAllAmazonGiftCardCountries;
import static bisq.desktop.util.FormBuilder.addComboBox;
import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextField;
import static bisq.desktop.util.FormBuilder.addLabel;

public class UpdateAmazonGiftCardAccountWindow extends Overlay<UpdateAmazonGiftCardAccountWindow> {
    private final AmazonGiftCardAccount amazonGiftCardAccount;
    private final User user;
    private ComboBox<Country> countryCombo;

    public UpdateAmazonGiftCardAccountWindow(AmazonGiftCardAccount amazonGiftCardAccount, User user) {
        super();
        this.amazonGiftCardAccount = amazonGiftCardAccount;
        this.user = user;
        type = Type.Attention;
        hideCloseButton = true;
        actionButtonText = Res.get("shared.save");
    }

    @Override
    protected void setupKeyHandler(Scene scene) {
        // We do not support enter or escape here
    }

    @Override
    public void show() {
        if (headLine == null)
            headLine = Res.get("payment.amazonGiftCard.upgrade.headLine");

        width = 868;
        createGridPane();
        addHeadLine();
        addContent();
        addButtons();
        applyStyles();
        display();
        // when there is only one possible country to choose from just go ahead and choose it. e.g. UK, US, JP etc.
        if (countryCombo.getItems().size() == 1) {
            countryCombo.setValue(countryCombo.getItems().get(0));
            UserThread.runAfter(() -> actionButton.fire(), 300, TimeUnit.MILLISECONDS);
        }
    }

    private void addContent() {
        addLabel(gridPane, ++rowIndex, Res.get("payment.account.amazonGiftCard.addCountryInfo", Res.get("payment.amazonGiftCard.upgrade"), amazonGiftCardAccount.getAccountName()));
        addCompactTopLabelTextField(gridPane, ++rowIndex, Res.get("shared.currency"), amazonGiftCardAccount.getSingleTradeCurrency().getNameAndCode());
        countryCombo = addComboBox(gridPane, ++rowIndex, Res.get("shared.country"));
        countryCombo.setPromptText(Res.get("payment.select.country"));
        countryCombo.setItems(FXCollections.observableArrayList(getAppropriateCountries(amazonGiftCardAccount.getSingleTradeCurrency().getCode())));
        countryCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Country country) {
                return country.name + " (" + country.code + ")";
            }
            @Override
            public Country fromString(String s) {
                return null;
            }
        });
        countryCombo.setOnAction(e -> {
            Country countryCode = countryCombo.getValue();
            actionButton.setDisable(countryCode == null || countryCode.code == null || countryCode.code.length() < 1);
        });
    }

    @Override
    protected void addButtons() {
        super.addButtons();
        Country countryCode = countryCombo.getValue();
        if (countryCode == null || countryCode.code == null || countryCode.code.isEmpty())
            actionButton.setDisable(true);
        // We do not allow close in case the field is not correctly added
        actionButton.setOnAction(event -> {
            Country chosenCountryCode = countryCombo.getValue();
            if (chosenCountryCode != null && chosenCountryCode.code != null && !chosenCountryCode.code.isEmpty()) {
                amazonGiftCardAccount.setCountry(chosenCountryCode);
                user.requestPersistence();
                closeHandlerOptional.ifPresent(Runnable::run);
                hide();
            }
        });
    }

    public static List<Country> getAppropriateCountries(String currency) {
        List<Country> list = new ArrayList<>();
        if (currency.equalsIgnoreCase("EUR")) {
            // Eurozone countries using EUR
            list = getAllAmazonGiftCardCountries();
            list = list.stream().filter(e -> e.code.matches("FR|DE|IT|NL|ES")).collect(Collectors.toList());
        } else {
            // non-Eurozone with own ccy
            HashMap<String, String> mapCcyToCountry = new HashMap<>(Map.of(
                    "AUD", "AU",
                    "CAD", "CA",
                    "GBP", "GB",
                    "INR", "IN",
                    "JPY", "JP",
                    "SAR", "SA",
                    "SEK", "SE",
                    "SGD", "SG",
                    "TRY", "TR",
                    "USD", "US"
            ));
            Optional<Country> found = findCountryByCode(mapCcyToCountry.get(currency));
            if (found.isPresent())
                list.add(found.get());
        }
        return list;
    }
}
