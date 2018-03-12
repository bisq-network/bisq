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

import javafx.scene.control.ComboBox;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

public class SearchComboBox<T> extends ComboBox<T> {
    @SuppressWarnings("CanBeFinal")
    private FilteredList<T> filteredList;

    public SearchComboBox() {
        this(FXCollections.<T>observableArrayList());
    }

    public SearchComboBox(final ObservableList<T> items) {
        super(new FilteredList<>(items));
        filteredList = new FilteredList<>(items);
        setEditable(true);

        itemsProperty().addListener((observable, oldValue, newValue) -> {
            filteredList = new FilteredList<>(newValue);
            setItems(filteredList);
        });
        getEditor().textProperty().addListener((observable, oldValue, newValue) -> {
            if (!filteredList.stream().filter(item -> getConverter().toString(item).equals(newValue)).
                    findAny().isPresent()) {
                UserThread.execute(() -> {
                    filteredList.setPredicate(item -> newValue.isEmpty() ||
                            getConverter().toString(item).toLowerCase().contains(newValue.toLowerCase()));
                    hide();
                    setVisibleRowCount(Math.min(12, filteredList.size()));
                    show();
                });
            }
        });
    }
}
