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

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutocompleteComboBox;
import bisq.desktop.components.BisqTextArea;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.ImageUtil;
import bisq.desktop.util.Layout;

import bisq.core.locale.Res;
import bisq.core.offer.OfferDirection;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.user.TradeGreeting;

import bisq.common.proto.ProtoUtil;
import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;
import javafx.geometry.Pos;

import javafx.event.EventHandler;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import javafx.util.Callback;
import javafx.util.StringConverter;

import java.util.List;

import lombok.Getter;

import static bisq.desktop.util.FormBuilder.*;
import static javafx.beans.binding.Bindings.createBooleanBinding;

public class EditGreetingsWindow extends Overlay<EditGreetingsWindow> {
    // TODO: would be nice to have header captions for the two combo boxes..

    private BisqTextArea nameInputTextField;
    private AutocompleteComboBox<OfferDirection> offerDirectionComboBox;
    private AutocompleteComboBox<PaymentMethod> paymentMethodComboBox;
    private ListView<TradeGreeting> listView;
    @Getter
    private ObservableList<TradeGreeting> listViewItems;

    public EditGreetingsWindow(List<TradeGreeting> available) {
        listView = new ListView<>();
        listViewItems = FXCollections.observableArrayList(available);
        listView.setItems(listViewItems);
        headLine = Res.get("settings.preferences.editGreetings.headline");
    }

    public void show() {
        width = 1000;
        createGridPane();
        addHeadLine();
        addContent();
        addButtons();
        applyStyles();
        display();
    }

    @Override
    protected void createGridPane() {
        gridPane = new GridPane();
        gridPane.setHgap(15);
        gridPane.setVgap(15);
        gridPane.setPadding(new Insets(64, 64, 64, 64));
        gridPane.setPrefWidth(width);
    }

    private void removeListViewItem(TradeGreeting item) {
        for (int x=0; x<listViewItems.size(); x++) {
            if (item.equalsIgnoringGreetingText(listViewItems.get(x))) {
                listViewItems.remove(x);
                return;
            }
        }
    }

    private boolean isPresent(TradeGreeting item) {
        for (int x=0; x<listViewItems.size(); x++) {
            if (item.equalsIgnoringGreetingText(listViewItems.get(x))) {
                return true;
            }
        }
        return false;
    }

    private void populateEdits(TradeGreeting x) {
        nameInputTextField.setText(x.getGreeting());
        offerDirectionComboBox.setValue(x.getOfferDirection().length() == 0 ? null : OfferDirection.valueOf(x.getOfferDirection()));
        paymentMethodComboBox.setValue(PaymentMethod.getPaymentMethod(x.getPaymentMethodId()));
    }

    private void addContent() {
        Label mlm = addMultilineLabel(gridPane, rowIndex++, Res.get("settings.preferences.editGreetings.description"), 0);
        GridPane.setMargin(mlm, new Insets(40, 0, 30, 0));

        nameInputTextField = new BisqTextArea();
        nameInputTextField.setLabelFloat(true);
        nameInputTextField.setPrefWidth(Layout.INITIAL_WINDOW_WIDTH * 0.5);
        nameInputTextField.setPromptText(Res.get("settings.preferences.editGreetings.text"));
        nameInputTextField.setPrefHeight(70);
        nameInputTextField.setWrapText(true);

        Tuple3<VBox, Label, AutocompleteComboBox<OfferDirection>> directionBoxTuple = addTopLabelAutocompleteComboBox(
                Res.get("settings.preferences.editGreetings.offerType"));
        offerDirectionComboBox = directionBoxTuple.third;
        offerDirectionComboBox.setPrefWidth(270);
        List<OfferDirection> offerDirections = List.of(OfferDirection.BUY, OfferDirection.SELL);
        offerDirectionComboBox.setAutocompleteItems(offerDirections);
        offerDirectionComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(OfferDirection object) {
                return object == null ? "" : object.toString();
            }
            @Override
            public OfferDirection fromString(String string) { return ProtoUtil.enumFromProto(OfferDirection.class, string);
            }
        });

        Tuple3<VBox, Label, AutocompleteComboBox<PaymentMethod>> paymentBoxTuple = addTopLabelAutocompleteComboBox(
                Res.get("settings.preferences.editGreetings.paymentMethod"));
        paymentMethodComboBox = paymentBoxTuple.third;
        paymentMethodComboBox.setCellFactory(GUIUtil.getPaymentMethodCellFactory());
        paymentMethodComboBox.setPrefWidth(270);
        paymentMethodComboBox.setAutocompleteItems(PaymentMethod.getPaymentMethods());
        paymentMethodComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(PaymentMethod object) {
                return object == null ? "" : object.getId();
            }
            @Override
            public PaymentMethod fromString(String string) { return PaymentMethod.getPaymentMethod(string);
            }
        });

        Button buttonAdd = new AutoTooltipButton(Res.get("settings.preferences.editGreetings.add"));
        buttonAdd.setStyle("-fx-min-width: 36; -fx-pref-height: 26; -fx-padding: 0 0 0 0;");
        buttonAdd.managedProperty().bind(buttonAdd.visibleProperty());
        buttonAdd.disableProperty().bind(createBooleanBinding(() -> nameInputTextField.getText().length() < 1000 && nameInputTextField.getText().length() > 10));

        buttonAdd.setOnAction(e -> {
            if (nameInputTextField.getText().length() > 0) {
                PaymentMethod paymentMethod = paymentMethodComboBox.getSelectionModel().getSelectedItem();
                TradeGreeting toBeAdded = new TradeGreeting(
                        nameInputTextField.getText(),
                        offerDirectionComboBox.getSelectionModel().getSelectedItem(),
                        paymentMethod == null || paymentMethod.equals(PaymentMethod.getPaymentMethod("")) ? "" : paymentMethod.getId());
                if (isPresent(toBeAdded)) {
                    removeListViewItem(toBeAdded);
                }
                listViewItems.add(toBeAdded);
                listView.setItems(listViewItems);
            }
        });
        HBox buttonBox = new HBox(nameInputTextField, directionBoxTuple.first, paymentBoxTuple.first);
        buttonBox.setSpacing(20);
        gridPane.add(buttonBox, 0, rowIndex++);

        HBox buttonBox2 = new HBox(buttonAdd);
        buttonBox2.setSpacing(20);
        buttonBox2.setAlignment(Pos.CENTER);
        gridPane.add(buttonBox2, 0, rowIndex++);

        ++rowIndex;
        final Tuple2<Label, VBox> topLabelWithVBox = getTopLabelWithVBox(Res.get("settings.preferences.editGreetings.available"), listView);
        listView.setPrefWidth(Layout.INITIAL_WINDOW_WIDTH);
        listView.setMaxHeight(150);
        gridPane.add(topLabelWithVBox.second, 0, ++rowIndex);

        listView.setCellFactory(new Callback<>() {
            @Override
            public ListCell<TradeGreeting> call(ListView<TradeGreeting> list) {
                ListCell<TradeGreeting> cell = new ListCell<>() {
                    final Label label = new AutoTooltipLabel();
                    final ImageView icon = ImageUtil.getImageViewById(ImageUtil.REMOVE_ICON);
                    final Button removeButton = new AutoTooltipButton("", icon);
                    final AnchorPane pane = new AnchorPane(label, removeButton);

                    {
                        label.setLayoutY(5);
                        removeButton.setId("icon-button");
                        AnchorPane.setRightAnchor(removeButton, -30d);
                    }

                    @Override
                    public void updateItem(final TradeGreeting item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            label.setText(item.toString());
                            removeButton.setOnAction(e -> {
                                populateEdits(item);
                                removeListViewItem(item);
                                listView.setItems(listViewItems);
                            });
                            setGraphic(pane);
                        } else {
                            setGraphic(null);
                            removeButton.setOnAction(null);
                        }
                    }
                };

                cell.addEventFilter(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
                    @Override
                    public void handle(MouseEvent event) {
                        if (event.getClickCount() == 2) {
                            populateEdits(listView.getSelectionModel().getSelectedItem());
                        }
                    }
                });
                return cell;
            }
        });
    }
}
