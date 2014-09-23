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

package io.bitsquare.gui.main.trade.createoffer.uimock;

import io.bitsquare.gui.components.confidence.ConfidenceProgressIndicator;

import java.net.URL;

import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateOfferControllerUIMock implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(CreateOfferControllerUIMock.class);


    @FXML private GridPane gridPane;
    @FXML private VBox priceAmountMinAmountBox, priceAmountBuyIconBox;
    @FXML private HBox priceAmountHBox;
    @FXML private ImageView priceAmountInfoIcon, payFundsInfoIcon, paymentInfoIcon, showDetailsInfoIcon;
    @FXML private Separator totalsSeparator;

    @FXML private Pane priceAmountPane, paymentInfoPane, payFundsPane, showDetailsPane;
    @FXML private Label priceAmountInfoLabel, priceAmountTitleLabel, paymentInfoTitleLabel, addressLabel,
            balanceLabel, paymentInfoLabel,
            payFundsInfoLabel, payFundsTitleLabel, offerFeeLabel, networkFeeLabel, summaryBtcLabel, totalToPayLabel,
            showDetailsTitleLabel, bankAccountTypeLabel, bankAccountCurrencyLabel, bankAccountCountyLabel,
            acceptedCountriesLabel, acceptedLanguagesLabel, acceptedArbitratorsLabel, showDetailsInfoLabel;
    @FXML private Button showPaymentInfoScreenButton, showPayFundsScreenButton, showDetailsButton;

    @FXML private TextField offerFeeTextField, networkFeeTextField, acceptedArbitratorsTextField;
    @FXML private TextField addressTextField;
    @FXML private TextField balanceTextField;

    @FXML private Label buyLabel, confirmationLabel, txTitleLabel, collateralLabel;
    @FXML private TextField amountTextField, minAmountTextField, priceTextField, volumeTextField;
    @FXML private Button placeOfferButton, closeButton;
    @FXML private TextField totalToPayTextField, collateralTextField, bankAccountTypeTextField,
            bankAccountCurrencyTextField, bankAccountCountyTextField, acceptedCountriesTextField,
            acceptedLanguagesTextField,
            summaryBtcTextField, transactionIdTextField;
    @FXML private ConfidenceProgressIndicator progressIndicator;


    @Inject
    private CreateOfferControllerUIMock() {
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
/*
        paymentInfoPane.setVisible(false);
        collateralLabel.setVisible(false);
        collateralTextField.setVisible(false);
        offerFeeLabel.setVisible(false);
        offerFeeTextField.setVisible(false);
        networkFeeLabel.setVisible(false);
        networkFeeTextField.setVisible(false);
        totalsSeparator.setVisible(false);
        summaryBtcLabel.setVisible(false);
        summaryBtcTextField.setVisible(false);
        paymentInfoLabel.setVisible(false);
        paymentInfoIcon.setVisible(false);
        showPayFundsScreenButton.setVisible(false);

        payFundsPane.setVisible(false);
        totalToPayLabel.setVisible(false);
        totalToPayTextField.setVisible(false);
        addressLabel.setVisible(false);
        addressTextField.setVisible(false);
        balanceLabel.setVisible(false);
        balanceTextField.setVisible(false);
        payFundsInfoIcon.setVisible(false);
        payFundsInfoLabel.setVisible(false);
        placeOfferButton.setVisible(false);
        showDetailsButton.setVisible(false);

        showDetailsPane.setVisible(false);
        showDetailsTitleLabel.setVisible(false);
        acceptedCountriesLabel.setVisible(false);
        acceptedCountriesTextField.setVisible(false);
        acceptedLanguagesLabel.setVisible(false);
        acceptedLanguagesTextField.setVisible(false);
        acceptedArbitratorsLabel.setVisible(false);
        acceptedArbitratorsTextField.setVisible(false);
        bankAccountTypeLabel.setVisible(false);
        bankAccountTypeTextField.setVisible(false);
        bankAccountCurrencyLabel.setVisible(false);
        bankAccountCurrencyTextField.setVisible(false);
        bankAccountCountyLabel.setVisible(false);
        bankAccountCountyTextField.setVisible(false);
        showDetailsInfoIcon.setVisible(false);
        showDetailsInfoLabel.setVisible(false);*/
    }

   /* @FXML
    private void collapsePriceAmountPane() {
        for (int i = 1; i < 4; i++) {
            gridPane.getRowConstraints().get(i).setMaxHeight(0);
        }

        GridPane.setRowSpan(priceAmountPane, 1);
        priceAmountBuyIconBox.setMaxHeight(0);

       // priceAmountPane.setVisible(false);
       // priceAmountTitleLabel.setVisible(false);
        priceAmountBuyIconBox.setVisible(false);
        priceAmountHBox.setVisible(false);
        priceAmountMinAmountBox.setVisible(false);
        priceAmountInfoIcon.setVisible(false);
        priceAmountInfoLabel.setVisible(false);
    }*/

    @FXML
    private void showPaymentInfoScreen() {

        priceAmountPane.setId("form-group-background");
        priceAmountTitleLabel.setId("form-group-title");
        showPaymentInfoScreenButton.setVisible(false);

        paymentInfoPane.setVisible(true);
        collateralLabel.setVisible(true);
        collateralTextField.setVisible(true);
        offerFeeLabel.setVisible(true);
        offerFeeTextField.setVisible(true);
        networkFeeLabel.setVisible(true);
        networkFeeTextField.setVisible(true);
        totalsSeparator.setVisible(true);
        summaryBtcLabel.setVisible(true);
        summaryBtcTextField.setVisible(true);
        paymentInfoLabel.setVisible(true);
        paymentInfoIcon.setVisible(true);
        showPayFundsScreenButton.setVisible(true);

        showPayFundsScreen();
    }

    @FXML
    private void showPayFundsScreen() {
        paymentInfoPane.setId("form-group-background");
        paymentInfoTitleLabel.setId("form-group-title");

        showPayFundsScreenButton.setVisible(false);

        payFundsPane.setVisible(true);
        totalToPayLabel.setVisible(true);
        totalToPayTextField.setVisible(true);
        addressLabel.setVisible(true);
        addressTextField.setVisible(true);
        balanceLabel.setVisible(true);
        balanceTextField.setVisible(true);
        payFundsInfoIcon.setVisible(true);
        payFundsInfoLabel.setVisible(true);
        placeOfferButton.setVisible(true);
        showDetailsButton.setVisible(true);

    }

    @FXML
    private void showDetailsScreen() {
        payFundsPane.setId("form-group-background");
        payFundsTitleLabel.setId("form-group-title");

        showDetailsButton.setManaged(false);
        showDetailsButton.setVisible(false);

        showDetailsPane.setVisible(true);
        showDetailsTitleLabel.setVisible(true);

        acceptedCountriesLabel.setVisible(true);
        acceptedCountriesTextField.setVisible(true);
        acceptedLanguagesLabel.setVisible(true);
        acceptedLanguagesTextField.setVisible(true);
        acceptedArbitratorsLabel.setVisible(true);
        acceptedArbitratorsTextField.setVisible(true);

        bankAccountTypeLabel.setVisible(true);
        bankAccountTypeTextField.setVisible(true);
        bankAccountCurrencyLabel.setVisible(true);
        bankAccountCurrencyTextField.setVisible(true);
        bankAccountCountyLabel.setVisible(true);
        bankAccountCountyTextField.setVisible(true);

        showDetailsInfoIcon.setVisible(true);
        showDetailsInfoLabel.setVisible(true);
    }


}

