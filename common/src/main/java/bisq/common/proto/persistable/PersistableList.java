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

package bisq.common.proto.persistable;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode
public abstract class PersistableList<T extends PersistablePayload> implements PersistableEnvelope {

    @Getter
    public final List<T> list = createList();

    protected List<T> createList() {
        return new ArrayList<>();
    }

    public PersistableList() {
    }

    public PersistableList(List<T> list) {
        setAll(list);
    }

    public void addListener(ListChangeListener<T> listener) {
        ((ObservableList<T>) getList()).addListener(listener);
    }

    public void removeListener(ListChangeListener<T> listener) {
        ((ObservableList<T>) getList()).removeListener(listener);
    }

    public ObservableList<T> getObservableList() {
        return (ObservableList<T>) getList();
    }

    public void setAll(Collection<T> collection) {
        this.list.clear();
        this.list.addAll(collection);
    }

    public boolean add(T item) {
        if (!list.contains(item)) {
            list.add(item);
            return true;
        }
        return false;
    }

    public boolean remove(T tradable) {
        return list.remove(tradable);
    }

    public Stream<T> stream() {
        return list.stream();
    }

    public int size() {
        return list.size();
    }

    public boolean contains(T thing) {
        return list.contains(thing);
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    public void forEach(Consumer<? super T> action) {
        list.forEach(action);
    }

    public void clear() {
        list.clear();
    }
}
