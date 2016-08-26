package io.bitsquare.gui.components;

import io.bitsquare.common.UserThread;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.ComboBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchComboBox<T> extends ComboBox<T> {
    private static final Logger log = LoggerFactory.getLogger(SearchComboBox.class);
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