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

package io.bitsquare.gui.main.account.arbitratorregistration;


import io.bitsquare.app.BitsquareApp;
import io.bitsquare.arbitration.Arbitrator;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.main.popups.EnterPrivKeyPopup;
import io.bitsquare.gui.main.popups.Popup;
import io.bitsquare.gui.util.FormBuilder;
import io.bitsquare.gui.util.ImageUtil;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.locale.LanguageUtil;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.util.Callback;
import javafx.util.StringConverter;

import javax.inject.Inject;

import static io.bitsquare.gui.util.FormBuilder.*;

@FxmlView
public class ArbitratorRegistrationView extends ActivatableViewAndModel<VBox, ArbitratorRegistrationViewModel> {

    private TextField pubKeyTextField;
    private ListView<String> languagesListView;
    private ComboBox<String> languageComboBox;

    private int gridRow = 0;
    private Button registerButton;
    private Button revokeButton;

    private ChangeListener<Arbitrator> arbitratorChangeListener;
    private EnterPrivKeyPopup enterPrivKeyPopup;
    private ListChangeListener<String> listChangeListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ArbitratorRegistrationView(ArbitratorRegistrationViewModel model) {
        super(model);
    }

    @Override
    public void initialize() {
        buildUI();

        languageComboBox.setItems(model.allLanguageCodes);

        arbitratorChangeListener = (observable, oldValue, arbitrator) -> updateLanguageList();
    }

    @Override
    protected void activate() {
    }

    @Override
    protected void deactivate() {
        model.myArbitratorProperty.removeListener(arbitratorChangeListener);
        languagesListView.getItems().removeListener(listChangeListener);
    }

    public void onTabSelection(boolean isSelectedTab) {
        if (isSelectedTab) {
            model.myArbitratorProperty.addListener(arbitratorChangeListener);
            updateLanguageList();

            if (model.registrationPubKeyAsHex.get() == null && enterPrivKeyPopup == null) {
                enterPrivKeyPopup = new EnterPrivKeyPopup();
                enterPrivKeyPopup.onClose(() -> enterPrivKeyPopup = null)
                        .onKey(privKey -> model.setPrivKeyAndCheckPubKey(privKey))
                        .width(700)
                        .show();
            }
        } else {
            model.myArbitratorProperty.removeListener(arbitratorChangeListener);
        }
    }

    private void updateLanguageList() {
        languagesListView.setItems(model.languageCodes);
        languagesListView.setPrefHeight(languagesListView.getItems().size() * Layout.LIST_ROW_HEIGHT + 2);
        listChangeListener = c -> languagesListView.setPrefHeight(languagesListView.getItems().size() * Layout.LIST_ROW_HEIGHT + 2);
        languagesListView.getItems().addListener(listChangeListener);
    }

    private void buildUI() {
        GridPane gridPane = new GridPane();
        gridPane.setPadding(new Insets(30, 25, -1, 25));
        gridPane.setHgap(5);
        gridPane.setVgap(5);
        ColumnConstraints columnConstraints1 = new ColumnConstraints();
        columnConstraints1.setHalignment(HPos.RIGHT);
        columnConstraints1.setHgrow(Priority.SOMETIMES);
        columnConstraints1.setMinWidth(200);
        ColumnConstraints columnConstraints2 = new ColumnConstraints();
        columnConstraints2.setHgrow(Priority.ALWAYS);
        gridPane.getColumnConstraints().addAll(columnConstraints1, columnConstraints2);
        root.getChildren().add(gridPane);

        addTitledGroupBg(gridPane, gridRow, 3, "Arbitrator registration");
        pubKeyTextField = FormBuilder.addLabelTextField(gridPane, gridRow, "Public key:",
                model.registrationPubKeyAsHex.get(), Layout.FIRST_ROW_DISTANCE).second;

        if (BitsquareApp.DEV_MODE)
            pubKeyTextField.setText("6ac43ea1df2a290c1c8391736aa42e4339c5cb4f110ff0257a13b63211977b7a");

        pubKeyTextField.textProperty().bind(model.registrationPubKeyAsHex);

        Tuple2<Label, ListView> tuple = addLabelListView(gridPane, ++gridRow, "Your languages:");
        GridPane.setValignment(tuple.first, VPos.TOP);
        languagesListView = tuple.second;
        languagesListView.disableProperty().bind(model.registrationEditDisabled);
        languagesListView.setMinHeight(3 * Layout.LIST_ROW_HEIGHT + 2);
        languagesListView.setMaxHeight(6 * Layout.LIST_ROW_HEIGHT + 2);
        languagesListView.setCellFactory(new Callback<ListView<String>, ListCell<String>>() {
            @Override
            public ListCell<String> call(ListView<String> list) {
                return new ListCell<String>() {
                    final Label label = new Label();
                    final ImageView icon = ImageUtil.getImageViewById(ImageUtil.REMOVE_ICON);
                    final Button removeButton = new Button("", icon);
                    final AnchorPane pane = new AnchorPane(label, removeButton);

                    {
                        label.setLayoutY(5);
                        removeButton.setId("icon-button");
                        AnchorPane.setRightAnchor(removeButton, 0d);
                    }

                    @Override
                    public void updateItem(final String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            label.setText(LanguageUtil.getDisplayName(item));
                            removeButton.setOnAction(e -> onRemoveLanguage(item));
                            setGraphic(pane);
                        } else {
                            setGraphic(null);
                        }
                    }
                };
            }
        });

        languageComboBox = addLabelComboBox(gridPane, ++gridRow).second;
        languageComboBox.disableProperty().bind(model.registrationEditDisabled);
        languageComboBox.setPromptText("Add language");
        languageComboBox.setConverter(new StringConverter<String>() {
            @Override
            public String toString(String code) {
                return LanguageUtil.getDisplayName(code);
            }

            @Override
            public String fromString(String s) {
                return null;
            }
        });
        languageComboBox.setOnAction(e -> onAddLanguage());

        registerButton = addButtonAfterGroup(gridPane, ++gridRow, "Register arbitrator");
        registerButton.disableProperty().bind(model.registrationEditDisabled);
        registerButton.setOnAction(e -> onRegister());

        revokeButton = addButton(gridPane, ++gridRow, "Revoke registration");
        revokeButton.setDefaultButton(false);
        revokeButton.disableProperty().bind(model.revokeButtonDisabled);
        revokeButton.setOnAction(e -> onRevoke());

        addTitledGroupBg(gridPane, ++gridRow, 2, "Information", Layout.GROUP_DISTANCE);
        Label infoLabel = addMultilineLabel(gridPane, gridRow);
        GridPane.setMargin(infoLabel, new Insets(Layout.FIRST_ROW_AND_GROUP_DISTANCE, 0, 0, 0));
        infoLabel.setText("Please note that you need to stay  available for 15 days after revoking as there might be trades which are using you as " +
                "arbitrator. The max. allowed trade period is 8 days and the dispute process might take up to 7 days.");
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onAddLanguage() {
        model.onAddLanguage(languageComboBox.getSelectionModel().getSelectedItem());
        UserThread.execute(() -> languageComboBox.getSelectionModel().clearSelection());
    }

    private void onRemoveLanguage(String locale) {
        model.onRemoveLanguage(locale);

        if (languagesListView.getItems().size() == 0) {
            new Popup().warning("You need to set at least 1 language.\nWe added the default language for you.").show();
            model.onAddLanguage(LanguageUtil.getDefaultLanguageLocaleAsCode());
        }
    }

    private void onRevoke() {
        if (model.isBootstrapped()) {
            model.onRevoke(
                    () -> new Popup().information("You have successfully removed your arbitrator from the P2P network.").show(),
                    (errorMessage) -> new Popup().error("Could not remove arbitrator.\nError message: " + errorMessage).show());
        } else {
            new Popup().warning("You need to wait until your client is bootstrapped in the network.\n" +
                    "That might take up to about 2 minutes at startup.").show();
        }
    }

    private void onRegister() {
        if (model.isBootstrapped()) {
            model.onRegister(
                    () -> new Popup().information("You have successfully registered your arbitrator to the P2P network.").show(),
                    (errorMessage) -> new Popup().error("Could not register arbitrator.\nError message: " + errorMessage).show());
        } else {
            new Popup().warning("You need to wait until your client is bootstrapped in the network.\n" +
                    "That might take up to about 2 minutes at startup.").show();
        }
    }
}
