package io.bisq.gui.main.funds.transactions;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;

import java.util.Collection;
import java.util.function.Consumer;

abstract class ObservableListDecorator<T> {
    private final ObservableList<T> delegate = FXCollections.observableArrayList();

    SortedList<T> asSortedList() {
        return new SortedList<>(delegate);
    }

    void forEach(Consumer<? super T> consumer) {
        delegate.forEach(consumer);
    }

    void setAll(Collection<? extends T> elements) {
        delegate.setAll(elements);
    }
}
