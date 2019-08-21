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

package bisq.desktop.components;

import bisq.common.UserThread;

import org.apache.commons.lang3.StringUtils;

import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.skins.JFXComboBoxListViewSkin;

import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import javafx.event.Event;
import javafx.event.EventHandler;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

public class SearchComboBox<T> extends JFXComboBox<T> {
    @SuppressWarnings("CanBeFinal")
    private FilteredList<T> filteredList;
    private ComboBoxListViewSkin comboBoxListViewSkin;

    public SearchComboBox() {
        this(FXCollections.observableArrayList());
    }

    private SearchComboBox(ObservableList<T> items) {
        super(items);
        setEditable(true);
        setEmptySkinToGetMoreControlOverListView();
        fixSpaceKey();
        wrapItemsInFilteredList();
        reactToQueryChanges();
    }

    // The ComboBox API does not provide enough control over the underlying
    // ListView that is used as a dropdown. The only way to get this control
    // is to set custom ListViewSkin. Default skin is null and so useless.
    private void setEmptySkinToGetMoreControlOverListView() {
        comboBoxListViewSkin = new JFXComboBoxListViewSkin<>(this);
        setSkin(comboBoxListViewSkin);
    }

    // By default pressing [SPACE] caused editor text to reset. The solution
    // is to suppress relevant event on the underlying ListViewSkin.
    private void fixSpaceKey() {
        comboBoxListViewSkin.getPopupContent().addEventFilter(KeyEvent.ANY, (KeyEvent event) -> {
            if (event.getCode() == KeyCode.SPACE)
                event.consume();
        });
    }

    // Whenever ComboBox.setItems() is called we need to intercept it
    // and wrap the physical list in a FilteredList view.
    // The default predicate is null meaning no filtering occurs.
    private void wrapItemsInFilteredList() {
        itemsProperty().addListener((obsValue, oldList, newList) -> {
            filteredList = new FilteredList<>(newList);
            setItems(filteredList);
        });
    }

    // Whenever query changes we need to reset the list-filter and refresh the ListView
    private void reactToQueryChanges() {
        getEditor().textProperty().addListener((observable, oldQuery, query) -> {
            var exactMatch = unfilteredItems().stream().anyMatch(item -> asString(item).equalsIgnoreCase(query));
            if (!exactMatch) {
                UserThread.execute(() -> {
                    if (query.isEmpty())
                        removeFilter();
                    else
                        filterBy(query);
                    forceRedraw();
                });
            }
        });
    }

    private ObservableList<T> unfilteredItems() {
        return (ObservableList<T>) filteredList.getSource();
    }

    private String asString(T item) {
        return getConverter().toString(item);
    }

    private int filteredItemsSize() {
        return filteredList.size();
    }

    private void removeFilter() {
        filteredList.setPredicate(null);
    }

    private void filterBy(String query) {
        filteredList.setPredicate(item ->
                StringUtils.containsIgnoreCase(asString(item), query)
        );
    }

     /**
      * Triggered when value change is *confirmed*. In practical terms
      * this is when user clicks item on the dropdown or hits [ENTER]
      * while typing in the text.
      *
      * This is in contrast to onAction event that is triggered
      * on every (unconfirmed) value change. The onAction is not really
      * suitable for the search enabled ComboBox.
     */
    public final void setOnChangeConfirmed(EventHandler<Event> eh) {
        setOnHidden(e -> {
            var selectedItem = getSelectionModel().getSelectedItem();
            var selectedItemText = asString(selectedItem);
            var inputText = getEditor().getText();
            if (inputText.equals(selectedItemText)) {
                eh.handle(e);
            }
        });
    }

    private void forceRedraw() {
        setVisibleRowCount(Math.min(10, filteredItemsSize()));
        if (filteredItemsSize() > 0) {
            comboBoxListViewSkin.getPopupContent().autosize();
            show();
        } else {
            hide();
        }
    }

    public void deactivate() {
        setOnHidden(null);
    }
}
