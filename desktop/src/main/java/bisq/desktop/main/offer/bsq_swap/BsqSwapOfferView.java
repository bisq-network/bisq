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

package bisq.desktop.main.offer.bsq_swap;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.FundsTextField;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.offer.OfferView;
import bisq.desktop.main.overlays.windows.BsqSwapOfferDetailsWindow;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;

import bisq.core.locale.Res;
import bisq.core.payment.PaymentAccount;

import bisq.common.util.Tuple3;

import org.bitcoinj.core.Coin;

import de.jensd.fx.glyphs.materialdesignicons.MaterialDesignIcon;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.VPos;

import javafx.beans.value.ChangeListener;

import javafx.util.Callback;

import static bisq.desktop.util.FormBuilder.add2ButtonsWithBox;
import static bisq.desktop.util.FormBuilder.addFundsTextfield;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;
import static javafx.scene.layout.Region.USE_COMPUTED_SIZE;

public abstract class BsqSwapOfferView<M extends BsqSwapOfferViewModel<?>> extends ActivatableViewAndModel<AnchorPane, M> {
    protected final Navigation navigation;
    protected final BsqSwapOfferDetailsWindow bsqSwapOfferDetailsWindow;

    protected ScrollPane scrollPane;
    protected GridPane gridPane;
    protected HBox nextButtonBar, actionButtonBar, firstRowHBox, secondRowHBox, amountValueCurrencyBox,
            volumeValueCurrencyBox, priceValueCurrencyBox, minAmountValueCurrencyBox;
    protected VBox paymentAccountVBox, currencyTextFieldBox;
    protected InputTextField amountTextField;
    protected Label resultLabel, xLabel, amountDescriptionLabel, priceCurrencyLabel, priceDescriptionLabel,
            volumeDescriptionLabel, volumeCurrencyLabel;
    protected Text xIcon;
    protected Button nextButton, cancelButton1, cancelButton2;
    protected AutoTooltipButton actionButton;
    protected TitledGroupBg paymentAccountTitledGroupBg, feeInfoTitledGroupBg;
    protected FundsTextField inputAmountTextField, payoutAmountTextField;
    protected ChangeListener<Boolean> amountFocusedListener;
    protected ChangeListener<Coin> missingFundsListener;
    protected OfferView.CloseHandler closeHandler;
    protected int gridRow = 0;
    protected boolean isMissingFundsPopupOpen;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BsqSwapOfferView(M model,
                            Navigation navigation,
                            BsqSwapOfferDetailsWindow bsqSwapOfferDetailsWindow) {
        super(model);

        this.navigation = navigation;
        this.bsqSwapOfferDetailsWindow = bsqSwapOfferDetailsWindow;
    }

    @Override
    protected void initialize() {
        super.initialize();

        addScrollPane();
        addGridPane();
        addPaymentAccountGroup();
        addAmountPriceGroup();
        addNextAndCancelButtons();
        addFeeInfoGroup();

        createListeners();

        GUIUtil.focusWhenAddedToScene(amountTextField);
    }

    @Override
    protected void activate() {
        super.activate();
    }

    @Override
    protected void deactivate() {
        super.deactivate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setCloseHandler(OfferView.CloseHandler closeHandler) {
        this.closeHandler = closeHandler;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handler
    ///////////////////////////////////////////////////////////////////////////////////////////

    public abstract void onTabSelected(boolean isSelected);

    protected abstract void onCancel1();

    protected void onShowFeeInfoScreen() {
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        nextButton.setVisible(false);
        nextButton.setManaged(false);
        nextButton.setOnAction(null);

        cancelButton1.setVisible(false);
        cancelButton1.setManaged(false);
        cancelButton1.setOnAction(null);

        actionButtonBar.setManaged(true);
        actionButtonBar.setVisible(true);

        feeInfoTitledGroupBg.setVisible(true);
        inputAmountTextField.setVisible(true);
        payoutAmountTextField.setVisible(true);

        updateOfferElementsStyle();
    }

    protected abstract void onCancel2();

    protected abstract void onAction();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void close() {
        if (closeHandler != null)
            closeHandler.close();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bindings, Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void createListeners() {
        missingFundsListener = (observable, oldValue, newValue) -> checkForMissingFunds(newValue);
    }

    protected abstract void addListeners();

    protected void removeListeners() {
        model.dataModel.getMissingFunds().removeListener(missingFundsListener);
    }

    protected abstract void addBindings();

    protected abstract void removeBindings();

    protected void addSubscriptions() {
    }

    protected void removeSubscriptions() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Build UI elements
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addScrollPane() {
        scrollPane = new ScrollPane();
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        AnchorPane.setLeftAnchor(scrollPane, 0d);
        AnchorPane.setTopAnchor(scrollPane, 0d);
        AnchorPane.setRightAnchor(scrollPane, 0d);
        AnchorPane.setBottomAnchor(scrollPane, 0d);
        root.getChildren().add(scrollPane);
    }

    private void addGridPane() {
        gridPane = new GridPane();
        gridPane.getStyleClass().add("content-pane");
        gridPane.setPadding(new Insets(30, 25, -1, 25));
        gridPane.setHgap(5);
        gridPane.setVgap(5);
        ColumnConstraints columnConstraints1 = new ColumnConstraints();
        columnConstraints1.setHalignment(HPos.RIGHT);
        columnConstraints1.setHgrow(Priority.NEVER);
        columnConstraints1.setMinWidth(200);
        ColumnConstraints columnConstraints2 = new ColumnConstraints();
        columnConstraints2.setHgrow(Priority.ALWAYS);
        gridPane.getColumnConstraints().addAll(columnConstraints1, columnConstraints2);
        scrollPane.setContent(gridPane);
    }

    protected abstract void addPaymentAccountGroup();

    protected abstract void addAmountPriceGroup();

    protected void addNextAndCancelButtons() {
        Tuple3<Button, Button, HBox> tuple = add2ButtonsWithBox(gridPane, ++gridRow,
                Res.get("shared.nextStep"), Res.get("shared.cancel"), 15, true);

        nextButtonBar = tuple.third;

        nextButton = tuple.first;
        nextButton.setMaxWidth(200);
        nextButton.setOnAction(e -> onShowFeeInfoScreen());

        cancelButton1 = tuple.second;
        cancelButton1.setMaxWidth(200);
        cancelButton1.setOnAction(e -> onCancel1());
    }

    protected void addFeeInfoGroup() {
        // don't increase gridRow as we removed button when this gets visible
        feeInfoTitledGroupBg = addTitledGroupBg(gridPane, gridRow, 2,
                Res.get("bsqSwapOffer.amounts.headline"), Layout.COMPACT_GROUP_DISTANCE);
        feeInfoTitledGroupBg.getStyleClass().add("last");
        feeInfoTitledGroupBg.setVisible(false);

        inputAmountTextField = addFundsTextfield(gridPane, gridRow,
                Res.get("bsqSwapOffer.inputAmount"), Layout.COMPACT_FIRST_ROW_AND_GROUP_DISTANCE);
        inputAmountTextField.setVisible(false);

        payoutAmountTextField = addFundsTextfield(gridPane, ++gridRow,
                Res.get("bsqSwapOffer.payoutAmount"));
        payoutAmountTextField.setVisible(false);

        inputAmountTextField.setPrefWidth(830);
        payoutAmountTextField.setPrefWidth(830);

        Tuple3<Button, Button, HBox> tuple = add2ButtonsWithBox(gridPane, ++gridRow,
                Res.get("shared.cancel"), Res.get("shared.cancel"), 5, false);

        actionButton = (AutoTooltipButton) tuple.first;
        actionButton.setMaxWidth(USE_COMPUTED_SIZE);
        actionButton.setOnAction(e -> onAction());

        cancelButton2 = tuple.second;
        cancelButton2.setMaxWidth(USE_COMPUTED_SIZE);
        cancelButton2.setOnAction(e -> onCancel2());

        actionButtonBar = tuple.third;
        actionButtonBar.setManaged(false);
        actionButtonBar.setVisible(false);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DetailsPopover
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected GridPane createInputAmountDetailsPopover() {
        GridPane infoGridPane = new GridPane();

        infoGridPane.setHgap(5);
        infoGridPane.setVgap(5);
        infoGridPane.setPadding(new Insets(10, 10, 10, 10));
        int i = 0;
        if (model.dataModel.isSellOffer()) {
            addPayInfoEntry(infoGridPane, i++, Res.getWithCol("shared.tradeAmount"), model.btcTradeAmount.get());
            addPayInfoEntry(infoGridPane, i++, Res.getWithCol("createOffer.fundsBox.networkFee"), model.getTxFee());
        } else {
            addPayInfoEntry(infoGridPane, i++, Res.getWithCol("shared.tradeAmount"), model.bsqTradeAmount.get());
            addPayInfoEntry(infoGridPane, i++, Res.getWithCol("createOffer.fundsBox.offerFee"), model.getTradeFee());
        }
        Separator separator = new Separator();
        separator.setOrientation(Orientation.HORIZONTAL);
        separator.getStyleClass().add("offer-separator");
        GridPane.setConstraints(separator, 1, i++);
        infoGridPane.getChildren().add(separator);
        addPayInfoEntry(infoGridPane, i, Res.getWithCol("shared.total"), model.inputAmount.get());
        return infoGridPane;
    }

    protected GridPane createPayoutAmountDetailsPopover() {
        GridPane infoGridPane = new GridPane();
        infoGridPane.setHgap(5);
        infoGridPane.setVgap(5);
        infoGridPane.setPadding(new Insets(10, 10, 10, 10));
        int i = 0;
        if (model.dataModel.isSellOffer()) {
            addPayInfoEntry(infoGridPane, i++, Res.getWithCol("shared.tradeAmount"), model.bsqTradeAmount.get());
            addPayInfoEntry(infoGridPane, i++, Res.getWithCol("createOffer.fundsBox.offerFee"), "- " + model.getTradeFee());
        } else {
            addPayInfoEntry(infoGridPane, i++, Res.getWithCol("shared.tradeAmount"), model.btcTradeAmount.get());
            addPayInfoEntry(infoGridPane, i++, Res.getWithCol("createOffer.fundsBox.networkFee"), "- " + model.getTxFee());
        }
        Separator separator = new Separator();
        separator.setOrientation(Orientation.HORIZONTAL);
        separator.getStyleClass().add("offer-separator");
        GridPane.setConstraints(separator, 1, i++);
        infoGridPane.getChildren().add(separator);
        addPayInfoEntry(infoGridPane, i, Res.getWithCol("shared.total"), model.payoutAmount.get());

        return infoGridPane;
    }

    private void addPayInfoEntry(GridPane infoGridPane, int row, String labelText, String value) {
        Label label = new AutoTooltipLabel(labelText);
        TextField textField = new TextField(value);
        textField.setMinWidth(300);
        textField.setEditable(false);
        textField.setFocusTraversable(false);
        textField.setId("payment-info");
        GridPane.setConstraints(label, 0, row, 1, 1, HPos.RIGHT, VPos.CENTER);
        GridPane.setConstraints(textField, 1, row);
        infoGridPane.getChildren().addAll(label, textField);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected Callback<ListView<PaymentAccount>, ListCell<PaymentAccount>> getPaymentAccountListCellFactory(
            ComboBox<PaymentAccount> paymentAccountsComboBox) {
        return GUIUtil.getPaymentAccountListCellFactory(paymentAccountsComboBox, model.getAccountAgeWitnessService());
    }

    protected void updateOfferElementsStyle() {
        String activeInputStyle = "input-with-border";
        String readOnlyInputStyle = "input-with-border-readonly";
        amountValueCurrencyBox.getStyleClass().remove(activeInputStyle);
        amountValueCurrencyBox.getStyleClass().add(readOnlyInputStyle);
        volumeValueCurrencyBox.getStyleClass().remove(activeInputStyle);
        volumeValueCurrencyBox.getStyleClass().add(readOnlyInputStyle);
        priceValueCurrencyBox.getStyleClass().remove(activeInputStyle);
        priceValueCurrencyBox.getStyleClass().add(readOnlyInputStyle);
        minAmountValueCurrencyBox.getStyleClass().remove(activeInputStyle);
        minAmountValueCurrencyBox.getStyleClass().add(readOnlyInputStyle);

        resultLabel.getStyleClass().add("small");
        xLabel.getStyleClass().add("small");
        xIcon.setStyle(String.format("-fx-font-family: %s; -fx-font-size: %s;", MaterialDesignIcon.CLOSE.fontFamily(), "1em"));
    }

    protected abstract void checkForMissingFunds(Coin newValue);

    protected void requestFocus() {
        // JFXComboBox causes a bug with requesting focus. Not clear why that happens but requesting a focus
        // on our view here avoids that the currency List overlay gets displayed.
        root.requestFocus();
    }
}
