package io.bisq.gui.main.funds.transactions;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import org.jetbrains.annotations.NotNull;

import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.Collection;
import java.util.Iterator;

class ObservableListDecorator<T> extends AbstractList<T> {
    private final ObservableList<T> delegate = FXCollections.observableArrayList();

    SortedList<T> asSortedList() {
        return new SortedList<>(delegate);
    }

    void setAll(Collection<? extends T> elements) {
        delegate.setAll(elements);
    }

    @Override
    public T get(int index) {
        return delegate.get(index);
    }

    @Override
    public int size() {
        return delegate.size();
    }
}
