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

package io.bitsquare.gui.main.account.content.paymentsaccount;

import io.bitsquare.common.util.Tuple2;
import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.common.view.Wizard;
import io.bitsquare.gui.components.TitledGroupBg;
import io.bitsquare.gui.components.paymentmethods.*;
import io.bitsquare.gui.popups.Popup;
import io.bitsquare.gui.util.FormBuilder;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.gui.util.validation.*;
import io.bitsquare.locale.BSResources;
import io.bitsquare.payment.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;

import javax.inject.Inject;

import static io.bitsquare.gui.util.FormBuilder.*;

@FxmlView
public class PaymentAccountView extends ActivatableViewAndModel<GridPane, PaymentAccountViewModel> implements Wizard.Step {

    private ComboBox<PaymentAccount> paymentAccountsComboBox;
    private ComboBox<PaymentMethod> paymentMethodsComboBox;

    private Wizard wizard;

    private final IBANValidator ibanValidator;
    private final BICValidator bicValidator;
    private final InputValidator inputValidator;
    private final OKPayValidator okPayValidator;
    private final AliPayValidator aliPayValidator;
    private final PerfectMoneyValidator perfectMoneyValidator;
    private final SwishValidator swishValidator;
    private final AltCoinAddressValidator altCoinAddressValidator;

    private PaymentMethodForm paymentMethodForm;
    private TitledGroupBg accountTitledGroupBg;
    private Button addAccountButton;
    private Button saveNewAccountButton;
    private int gridRow = 0;

    @Inject
    public PaymentAccountView(PaymentAccountViewModel model,
                              IBANValidator ibanValidator,
                              BICValidator bicValidator,
                              InputValidator inputValidator,
                              OKPayValidator okPayValidator,
                              AliPayValidator aliPayValidator,
                              PerfectMoneyValidator perfectMoneyValidator,
                              SwishValidator swishValidator,
                              AltCoinAddressValidator altCoinAddressValidator) {
        super(model);

        this.ibanValidator = ibanValidator;
        this.bicValidator = bicValidator;
        this.inputValidator = inputValidator;
        this.okPayValidator = okPayValidator;
        this.aliPayValidator = aliPayValidator;
        this.perfectMoneyValidator = perfectMoneyValidator;
        this.swishValidator = swishValidator;
        this.altCoinAddressValidator = altCoinAddressValidator;
    }

    @Override
    public void initialize() {
        buildForm();
    }

    @Override
    protected void activate() {
        paymentAccountsComboBox.setItems(model.getPaymentAccounts());
        EventHandler<ActionEvent> paymentAccountsComboBoxHandler = e -> {
            if (paymentAccountsComboBox.getSelectionModel().getSelectedItem() != null)
                onSelectAccount(paymentAccountsComboBox.getSelectionModel().getSelectedItem());
        };
        paymentAccountsComboBox.setOnAction(paymentAccountsComboBoxHandler);

        model.getPaymentAccounts().addListener(
                (ListChangeListener<PaymentAccount>) c -> paymentAccountsComboBox.setDisable(model.getPaymentAccounts().size() == 0));
        paymentAccountsComboBox.setDisable(model.getPaymentAccounts().size() == 0);
    }

    @Override
    protected void deactivate() {
        paymentAccountsComboBox.setOnAction(null);
    }

    @Override
    public void setWizard(Wizard wizard) {
        this.wizard = wizard;
    }

    @Override
    public void hideWizardNavigation() {

    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onSaveNewAccount(PaymentAccount paymentAccount) {
        if (!model.getPaymentAccounts().stream().filter(e -> {
            if (e.getAccountName() != null)
                return e.getAccountName().equals(paymentAccount.getAccountName());
            else
                return false;
        }).findAny().isPresent()) {
            model.onSaveNewAccount(paymentAccount);
            removeNewAccountForm();
            paymentAccountsComboBox.getSelectionModel().clearSelection();
        } else {
            new Popup().error("That account name is already used in a saved account. \nPlease use another name.").show();
        }
    }

    private void onCancelNewAccount() {
        removeNewAccountForm();
        paymentAccountsComboBox.getSelectionModel().clearSelection();
    }

    private void onDeleteAccount(PaymentAccount paymentAccount) {
        new Popup().warning("Do you really want to delete the selected payment account?")
                .onAction(() -> {
                    model.onDeleteAccount(paymentAccount);
                    removeSelectAccountForm();
                    paymentAccountsComboBox.getSelectionModel().clearSelection();
                })
                .show();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Base form
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void buildForm() {
        addTitledGroupBg(root, gridRow, 2, "Manage payment accounts");

        paymentAccountsComboBox = addLabelComboBox(root, gridRow, "Select account:", Layout.FIRST_ROW_DISTANCE).second;
        paymentAccountsComboBox.setPromptText("Select account");
        paymentAccountsComboBox.setConverter(new StringConverter<PaymentAccount>() {
            @Override
            public String toString(PaymentAccount paymentAccount) {
                return paymentAccount.getAccountName();
            }

            @Override
            public PaymentAccount fromString(String s) {
                return null;
            }
        });

        addAccountButton = addButton(root, ++gridRow, "Add new account");
        addAccountButton.setOnAction(event -> addNewAccount());
    }

    // Add new account form
    private void addNewAccount() {
        paymentAccountsComboBox.getSelectionModel().clearSelection();
        removeAccountRows();
        addAccountButton.setDisable(true);
        accountTitledGroupBg = addTitledGroupBg(root, ++gridRow, 1, "Create new account", Layout.GROUP_DISTANCE);
        paymentMethodsComboBox = addLabelComboBox(root, gridRow, "Payment method:", Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        paymentMethodsComboBox.setPromptText("Select payment method");
        paymentMethodsComboBox.setPrefWidth(250);
        paymentMethodsComboBox.setItems(FXCollections.observableArrayList(PaymentMethod.ALL_VALUES));
        paymentMethodsComboBox.setConverter(new StringConverter<PaymentMethod>() {
            @Override
            public String toString(PaymentMethod paymentMethod) {
                return BSResources.get(paymentMethod.getId());
            }

            @Override
            public PaymentMethod fromString(String s) {
                return null;
            }
        });
        paymentMethodsComboBox.setOnAction(e -> {
            if (paymentMethodForm != null) {
                FormBuilder.removeRowsFromGridPane(root, 3, paymentMethodForm.getGridRow() + 1);
                GridPane.setRowSpan(accountTitledGroupBg, paymentMethodForm.getRowSpan() + 1);
            }
            gridRow = 2;
            paymentMethodForm = getPaymentMethodForm(paymentMethodsComboBox.getSelectionModel().getSelectedItem());
            if (paymentMethodForm != null) {
                paymentMethodForm.addFormForAddAccount();
                gridRow = paymentMethodForm.getGridRow();
                Tuple2<Button, Button> tuple2 = add2ButtonsAfterGroup(root, ++gridRow, "Save new account", "Cancel");
                saveNewAccountButton = tuple2.first;
                saveNewAccountButton.setOnAction(event -> onSaveNewAccount(paymentMethodForm.getPaymentAccount()));
                saveNewAccountButton.disableProperty().bind(paymentMethodForm.allInputsValidProperty().not());
                Button cancelButton = tuple2.second;
                cancelButton.setOnAction(event -> onCancelNewAccount());
                GridPane.setRowSpan(accountTitledGroupBg, paymentMethodForm.getRowSpan() + 1);
            }
        });
    }

    // Select account form
    private void onSelectAccount(PaymentAccount paymentAccount) {
        removeAccountRows();
        addAccountButton.setDisable(false);
        accountTitledGroupBg = addTitledGroupBg(root, ++gridRow, 1, "Selected account", Layout.GROUP_DISTANCE);
        paymentMethodForm = getPaymentMethodForm(paymentAccount);
        if (paymentMethodForm != null) {
            paymentMethodForm.addFormForDisplayAccount();
            gridRow = paymentMethodForm.getGridRow();
            Button deleteAccountButton = addButtonAfterGroup(root, ++gridRow, "Delete account");
            deleteAccountButton.setOnAction(event -> onDeleteAccount(paymentMethodForm.getPaymentAccount()));
            GridPane.setRowSpan(accountTitledGroupBg, paymentMethodForm.getRowSpan());
            model.onSelectAccount(paymentAccount);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private PaymentMethodForm getPaymentMethodForm(PaymentAccount paymentAccount) {
        return getPaymentMethodForm(paymentAccount.getPaymentMethod(), paymentAccount);
    }

    private PaymentMethodForm getPaymentMethodForm(PaymentMethod paymentMethod) {
        PaymentAccount paymentAccount;
        switch (paymentMethod.getId()) {
            case PaymentMethod.OK_PAY_ID:
                paymentAccount = new OKPayAccount();
                break;
            case PaymentMethod.PERFECT_MONEY_ID:
                paymentAccount = new PerfectMoneyAccount();
                break;
            case PaymentMethod.SEPA_ID:
                paymentAccount = new SepaAccount();
                break;
            case PaymentMethod.ALI_PAY_ID:
                paymentAccount = new AliPayAccount();
                break;
            case PaymentMethod.SWISH_ID:
                paymentAccount = new SwishAccount();
                break;
            case PaymentMethod.BLOCK_CHAINS_ID:
                paymentAccount = new BlockChainAccount();
                break;
            default:
                log.error("Not supported PaymentMethod: " + paymentMethod);
                paymentAccount = null;
                break;
        }
        return getPaymentMethodForm(paymentMethod, paymentAccount);
    }

    private PaymentMethodForm getPaymentMethodForm(PaymentMethod paymentMethod, PaymentAccount paymentAccount) {
        switch (paymentMethod.getId()) {
            case PaymentMethod.OK_PAY_ID:
                return new OKPayForm(paymentAccount, okPayValidator, inputValidator, root, gridRow);
            case PaymentMethod.PERFECT_MONEY_ID:
                return new PerfectMoneyForm(paymentAccount, perfectMoneyValidator, inputValidator, root, gridRow);
            case PaymentMethod.SEPA_ID:
                return new SepaForm(paymentAccount, ibanValidator, bicValidator, inputValidator, root, gridRow);
            case PaymentMethod.ALI_PAY_ID:
                return new AliPayForm(paymentAccount, aliPayValidator, inputValidator, root, gridRow);
            case PaymentMethod.SWISH_ID:
                return new SwishForm(paymentAccount, swishValidator, inputValidator, root, gridRow);
            case PaymentMethod.BLOCK_CHAINS_ID:
                return new BlockChainForm(paymentAccount, altCoinAddressValidator, inputValidator, root, gridRow);
            default:
                log.error("Not supported PaymentMethod: " + paymentMethod);
                return null;
        }
    }

    private void removeNewAccountForm() {
        saveNewAccountButton.disableProperty().unbind();
        removeAccountRows();
        addAccountButton.setDisable(false);
    }

    private void removeSelectAccountForm() {
        FormBuilder.removeRowsFromGridPane(root, 2, gridRow);
        gridRow = 1;
        addAccountButton.setDisable(false);
    }


    private void removeAccountRows() {
        FormBuilder.removeRowsFromGridPane(root, 2, gridRow);
        gridRow = 1;
    }

}

