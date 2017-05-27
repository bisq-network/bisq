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

package io.bitsquare.gui.main.account.content.arbitratorselection;

import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.TableGroupHeadline;
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.account.content.ContentSettings;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.util.ImageUtil;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.locale.LanguageUtil;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.util.Callback;
import javafx.util.StringConverter;

import javax.inject.Inject;

import static io.bitsquare.gui.util.FormBuilder.*;

@FxmlView
public class ArbitratorSelectionView extends ActivatableViewAndModel<GridPane, ArbitratorSelectionViewModel> {

    private final ArbitratorSelectionViewModel model;

    private ListView<String> languagesListView;
    private ComboBox<String> languageComboBox;
    private TableView<ArbitratorListItem> tableView;
    private int gridRow = 0;
    private CheckBox autoSelectAllMatchingCheckBox;
    private ListChangeListener<String> listChangeListener;
    private ListChangeListener<String> languageCodesListChangeListener;
    private ChangeListener<Boolean> isSelectedChangeListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ArbitratorSelectionView(ArbitratorSelectionViewModel model) {
        super(model);
        this.model = model;
    }

    @Override
    public void initialize() {
//        ContentSettings.setDefaultSettings(root, 140);

        addLanguageGroup();
        addArbitratorsGroup();
        listChangeListener = c -> languagesListView.setPrefHeight(languagesListView.getItems().size() * Layout.LIST_ROW_HEIGHT + 2);
    }

    @Override
    protected void activate() {
        languagesListView.getItems().addListener(listChangeListener);
        languageComboBox.setItems(model.allLanguageCodes);
        languagesListView.setItems(model.languageCodes);
        languagesListView.setPrefHeight(languagesListView.getItems().size() * Layout.LIST_ROW_HEIGHT + 2);

        tableView.setItems(model.arbitratorListItems);
        autoSelectAllMatchingCheckBox.setSelected(model.getAutoSelectArbitrators());
    }

    @Override
    protected void deactivate() {
        languagesListView.getItems().removeListener(listChangeListener);
        if (languageCodesListChangeListener != null)
            model.languageCodes.removeListener(languageCodesListChangeListener);
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
            new Popup().warning("You need to set at least 1 language.\n" +
                    "We added the default language for you.").show();
            model.onAddLanguage(LanguageUtil.getDefaultLanguageLocaleAsCode());
        }
    }

    private void onAddArbitrator(ArbitratorListItem arbitratorListItem) {
        model.onAddArbitrator(arbitratorListItem.arbitrator);
    }

    private void onRemoveArbitrator(ArbitratorListItem arbitratorListItem) {
        model.onRemoveArbitrator(arbitratorListItem.arbitrator);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI builder
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addLanguageGroup() {
        addTitledGroupBg(root, gridRow, 1, "Which languages do you speak?");

        Tuple2<Label, ListView> tuple = addLabelListView(root, gridRow, "Your languages:", Layout.FIRST_ROW_DISTANCE);
        GridPane.setValignment(tuple.first, VPos.TOP);
        languagesListView = tuple.second;
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

        languageComboBox = addLabelComboBox(root, ++gridRow, "", 15).second;
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
    }

    private void addArbitratorsGroup() {
        TableGroupHeadline tableGroupHeadline = new TableGroupHeadline("Which arbitrators do you accept");
        GridPane.setRowIndex(tableGroupHeadline, ++gridRow);
        GridPane.setColumnSpan(tableGroupHeadline, 2);
        GridPane.setMargin(tableGroupHeadline, new Insets(40, -10, -10, -10));
        root.getChildren().add(tableGroupHeadline);

        tableView = new TableView<>();
        GridPane.setRowIndex(tableView, gridRow);
        GridPane.setColumnSpan(tableView, 2);
        GridPane.setMargin(tableView, new Insets(60, -10, 5, -10));
        root.getChildren().add(tableView);

        autoSelectAllMatchingCheckBox = addCheckBox(root, ++gridRow, "Auto select all arbitrators with matching language");
        GridPane.setColumnSpan(autoSelectAllMatchingCheckBox, 2);
        GridPane.setHalignment(autoSelectAllMatchingCheckBox, HPos.LEFT);
        GridPane.setColumnIndex(autoSelectAllMatchingCheckBox, 0);
        GridPane.setMargin(autoSelectAllMatchingCheckBox, new Insets(0, -10, 0, -10));
        autoSelectAllMatchingCheckBox.setOnAction(event -> model.setAutoSelectArbitrators(autoSelectAllMatchingCheckBox.isSelected()));

        TableColumn<ArbitratorListItem, String> dateColumn = new TableColumn("Registration date");
        dateColumn.setSortable(false);
        dateColumn.setCellValueFactory(param -> new ReadOnlyObjectWrapper(param.getValue().getRegistrationDate()));
        dateColumn.setMinWidth(140);
        dateColumn.setMaxWidth(140);

        TableColumn<ArbitratorListItem, String> nameColumn = new TableColumn("Onion address");
        nameColumn.setSortable(false);
        nameColumn.setCellValueFactory(param -> new ReadOnlyObjectWrapper(param.getValue().getAddressString()));
        nameColumn.setMinWidth(90);

        TableColumn<ArbitratorListItem, String> languagesColumn = new TableColumn("Languages");
        languagesColumn.setSortable(false);
        languagesColumn.setCellValueFactory(param -> new ReadOnlyObjectWrapper(param.getValue().getLanguageCodes()));
        languagesColumn.setMinWidth(130);

        TableColumn<ArbitratorListItem, ArbitratorListItem> selectionColumn = new TableColumn<ArbitratorListItem, ArbitratorListItem>("Accept") {
            {
                setMinWidth(60);
                setMaxWidth(60);
                setSortable(false);
            }
        };
        selectionColumn.setCellValueFactory((arbitrator) -> new ReadOnlyObjectWrapper<>(arbitrator.getValue()));
        selectionColumn.setCellFactory(
                new Callback<TableColumn<ArbitratorListItem, ArbitratorListItem>, TableCell<ArbitratorListItem, ArbitratorListItem>>() {
                    @Override
                    public TableCell<ArbitratorListItem, ArbitratorListItem> call(TableColumn<ArbitratorListItem, ArbitratorListItem> column) {
                        return new TableCell<ArbitratorListItem, ArbitratorListItem>() {
                            private final CheckBox checkBox = new CheckBox();
                            private TableRow tableRow;
                            private BooleanProperty selectedProperty;

                            private void updateDisableState(final ArbitratorListItem item) {
                                boolean selected = model.isAcceptedArbitrator(item.arbitrator);
                                item.setIsSelected(selected);

                                boolean hasMatchingLanguage = model.hasMatchingLanguage(item.arbitrator);
                                if (!hasMatchingLanguage) {
                                    model.onRemoveArbitrator(item.arbitrator);
                                    if (selected)
                                        item.setIsSelected(false);
                                }

                                boolean isMyOwnRegisteredArbitrator = model.isMyOwnRegisteredArbitrator(item.arbitrator);
                                checkBox.setDisable(!hasMatchingLanguage || isMyOwnRegisteredArbitrator);

                                tableRow = getTableRow();
                                if (tableRow != null) {
                                    tableRow.setOpacity(hasMatchingLanguage && !isMyOwnRegisteredArbitrator ? 1 : 0.4);

                                    if (isMyOwnRegisteredArbitrator) {
                                        tableRow.setTooltip(new Tooltip("An arbitrator cannot select himself for trading."));
                                        tableRow.setOnMouseClicked(e -> new Popup().warning(
                                                "An arbitrator cannot select himself for trading.").show());
                                    } else if (!hasMatchingLanguage) {
                                        tableRow.setTooltip(new Tooltip("No matching language."));
                                        tableRow.setOnMouseClicked(e -> new Popup().warning(
                                                "You can only select arbitrators who are speaking at least 1 common language.").show());
                                    } else {
                                        tableRow.setOnMouseClicked(null);
                                        tableRow.setTooltip(null);
                                    }
                                }
                            }

                            @Override
                            public void updateItem(final ArbitratorListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    selectedProperty = item.isSelectedProperty();
                                    languageCodesListChangeListener = c -> updateDisableState(item);
                                    model.languageCodes.addListener(languageCodesListChangeListener);

                                    isSelectedChangeListener = (observable, oldValue, newValue) -> checkBox.setSelected(newValue);
                                    selectedProperty.addListener(isSelectedChangeListener);

                                    checkBox.setSelected(model.isAcceptedArbitrator(item.arbitrator));
                                    checkBox.setOnAction(e -> {
                                                if (checkBox.isSelected()) {
                                                    onAddArbitrator(item);
                                                } else if (model.isDeselectAllowed(item)) {
                                                    onRemoveArbitrator(item);
                                                } else {
                                                    new Popup().warning("You need to have at least one arbitrator selected.").show();
                                                    checkBox.setSelected(true);
                                                }
                                                item.setIsSelected(checkBox.isSelected());
                                            }
                                    );

                                    updateDisableState(item);
                                    setGraphic(checkBox);
                                } else {
                                    model.languageCodes.removeListener(languageCodesListChangeListener);
                                    if (selectedProperty != null)
                                        selectedProperty.removeListener(isSelectedChangeListener);

                                    setGraphic(null);

                                    if (checkBox != null)
                                        checkBox.setOnAction(null);
                                    if (tableRow != null)
                                        tableRow.setOnMouseClicked(null);
                                }
                            }
                        };
                    }
                });

        tableView.getColumns().addAll(dateColumn, nameColumn, languagesColumn, selectionColumn);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }
}

