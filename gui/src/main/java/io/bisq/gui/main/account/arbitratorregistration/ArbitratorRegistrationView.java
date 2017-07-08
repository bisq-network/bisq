/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.main.account.arbitratorregistration;


import io.bisq.common.UserThread;
import io.bisq.common.locale.LanguageUtil;
import io.bisq.common.locale.Res;
import io.bisq.common.util.Tuple2;
import io.bisq.core.arbitration.Arbitrator;
import io.bisq.gui.common.view.ActivatableViewAndModel;
import io.bisq.gui.common.view.FxmlView;
import io.bisq.gui.main.MainView;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.main.overlays.windows.UnlockArbitrationRegistrationWindow;
import io.bisq.gui.util.FormBuilder;
import io.bisq.gui.util.ImageUtil;
import io.bisq.gui.util.Layout;
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

import static io.bisq.gui.util.FormBuilder.*;

@FxmlView
public class ArbitratorRegistrationView extends ActivatableViewAndModel<VBox, ArbitratorRegistrationViewModel> {

    private ListView<String> languagesListView;
    private ComboBox<String> languageComboBox;

    private int gridRow = 0;

    private ChangeListener<Arbitrator> arbitratorChangeListener;
    private UnlockArbitrationRegistrationWindow unlockArbitrationRegistrationWindow;
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

            if (model.registrationPubKeyAsHex.get() == null && unlockArbitrationRegistrationWindow == null) {
                unlockArbitrationRegistrationWindow = new UnlockArbitrationRegistrationWindow();
                unlockArbitrationRegistrationWindow.onClose(() -> unlockArbitrationRegistrationWindow = null)
                        .onKey(model::setPrivKeyAndCheckPubKey)
                        .width(MainView.scale(700))
                        .show();
            }
        } else {
            model.myArbitratorProperty.removeListener(arbitratorChangeListener);
        }
    }

    private void updateLanguageList() {
        languagesListView.setItems(model.languageCodes);
        languagesListView.setPrefHeight(MainView.scale(languagesListView.getItems().size() * Layout.LIST_ROW_HEIGHT + 2));
        listChangeListener = c -> languagesListView.setPrefHeight(MainView.scale(languagesListView.getItems().size() * Layout.LIST_ROW_HEIGHT + 2));
        languagesListView.getItems().addListener(listChangeListener);
    }

    private void buildUI() {
        root.setSpacing(MainView.scale(10));
        root.setPadding(new Insets(MainView.scale(0), MainView.scale(10), MainView.scale(10), MainView.scale(0)));

        GridPane gridPane = new GridPane();
        gridPane.setPadding(new Insets(MainView.scale(30), MainView.scale(25), MainView.scale(-1), MainView.scale(25)));
        gridPane.setHgap(MainView.scale(5));
        gridPane.setVgap(MainView.scale(5));
        ColumnConstraints columnConstraints1 = new ColumnConstraints();
        columnConstraints1.setHalignment(HPos.RIGHT);
        columnConstraints1.setHgrow(Priority.SOMETIMES);
        columnConstraints1.setMinWidth(MainView.scale(200));
        ColumnConstraints columnConstraints2 = new ColumnConstraints();
        columnConstraints2.setHgrow(Priority.ALWAYS);
        gridPane.getColumnConstraints().addAll(columnConstraints1, columnConstraints2);
        root.getChildren().add(gridPane);

        addTitledGroupBg(gridPane, gridRow, 3, Res.get("account.tab.arbitratorRegistration"));
        TextField pubKeyTextField = FormBuilder.addLabelTextField(gridPane, gridRow, Res.get("account.arbitratorRegistration.pubKey"),
                model.registrationPubKeyAsHex.get(), MainView.scale(Layout.FIRST_ROW_DISTANCE)).second;

        pubKeyTextField.textProperty().bind(model.registrationPubKeyAsHex);

        Tuple2<Label, ListView> tuple = addLabelListView(gridPane, ++gridRow, Res.get("shared.yourLanguage"));
        GridPane.setValignment(tuple.first, VPos.TOP);
        //noinspection unchecked
        languagesListView = tuple.second;
        languagesListView.disableProperty().bind(model.registrationEditDisabled);
        languagesListView.setMinHeight(MainView.scale(3 * Layout.LIST_ROW_HEIGHT + 2));
        languagesListView.setMaxHeight(MainView.scale(6 * Layout.LIST_ROW_HEIGHT + 2));
        languagesListView.setCellFactory(new Callback<ListView<String>, ListCell<String>>() {
            @Override
            public ListCell<String> call(ListView<String> list) {
                return new ListCell<String>() {
                    final Label label = new Label();
                    final ImageView icon = ImageUtil.getImageViewById(ImageUtil.REMOVE_ICON);
                    final Button removeButton = new Button("", icon);
                    final AnchorPane pane = new AnchorPane(label, removeButton);

                    {
                        label.setLayoutY(MainView.scale(5));
                        removeButton.setId("icon-button");
                        AnchorPane.setRightAnchor(removeButton, MainView.scale(0));
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

        //noinspection unchecked
        languageComboBox = addLabelComboBox(gridPane, ++gridRow).second;
        languageComboBox.disableProperty().bind(model.registrationEditDisabled);
        languageComboBox.setPromptText(Res.get("shared.addLanguage"));
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

        Button registerButton = addButtonAfterGroup(gridPane, ++gridRow, Res.get("account.arbitratorRegistration.register"));
        registerButton.disableProperty().bind(model.registrationEditDisabled);
        registerButton.setOnAction(e -> onRegister());

        Button revokeButton = addButton(gridPane, ++gridRow, Res.get("account.arbitratorRegistration.revoke"));
        revokeButton.setDefaultButton(false);
        revokeButton.disableProperty().bind(model.revokeButtonDisabled);
        revokeButton.setOnAction(e -> onRevoke());

        addTitledGroupBg(gridPane, ++gridRow, 2, Res.get("shared.information"), MainView.scale(Layout.GROUP_DISTANCE));
        Label infoLabel = addMultilineLabel(gridPane, gridRow);
        GridPane.setMargin(infoLabel, new Insets(MainView.scale(Layout.FIRST_ROW_AND_GROUP_DISTANCE), MainView.scale(0), MainView.scale(0), MainView.scale(0)));
        infoLabel.setText(Res.get("account.arbitratorRegistration.info.msg"));
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
            new Popup<>().warning(Res.get("account.arbitratorRegistration.warn.min1Language")).show();
            model.onAddLanguage(LanguageUtil.getDefaultLanguageLocaleAsCode());
        }
    }

    private void onRevoke() {
        if (model.isBootstrapped()) {
            model.onRevoke(
                    () -> new Popup<>().feedback(Res.get("account.arbitratorRegistration.removedSuccess")).show(),
                    (errorMessage) -> new Popup<>().error(Res.get("account.arbitratorRegistration.removedFailed",
                            Res.get("shared.errorMessageInline", errorMessage))).show());
        } else {
            new Popup<>().information(Res.get("popup.warning.notFullyConnected")).show();
        }
    }

    private void onRegister() {
        if (model.isBootstrapped()) {
            model.onRegister(
                    () -> new Popup<>().feedback(Res.get("account.arbitratorRegistration.registerSuccess")).show(),
                    (errorMessage) -> new Popup<>().error(Res.get("account.arbitratorRegistration.registerFailed",
                            Res.get("shared.errorMessageInline", errorMessage))).show());
        } else {
            new Popup<>().information(Res.get("popup.warning.notFullyConnected")).show();
        }
    }
}
