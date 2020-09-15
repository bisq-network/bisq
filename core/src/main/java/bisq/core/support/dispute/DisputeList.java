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

package bisq.core.support.dispute;

import bisq.common.proto.persistable.PersistableEnvelope;
import bisq.common.proto.persistable.UserThreadMappedPersistableEnvelope;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ToString
/*
 * Holds a List of Dispute objects.
 *
 * Calls to the List are delegated because this class intercepts the add/remove calls so changes
 * can be saved to disc.
 */
public abstract class DisputeList<T extends PersistableEnvelope> implements UserThreadMappedPersistableEnvelope {
    @Getter
    protected final ObservableList<Dispute> list = FXCollections.observableArrayList();

    public DisputeList() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected DisputeList(List<Dispute> list) {
        this.list.addAll(list);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean add(Dispute dispute) {
        if (!list.contains(dispute)) {
            list.add(dispute);
            return true;
        } else {
            return false;
        }
    }

    public boolean remove(Dispute dispute) {
        return list.remove(dispute);
    }

    public int size() {
        return list.size();
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    @SuppressWarnings({"SuspiciousMethodCalls"})
    public boolean contains(Object o) {
        return list.contains(o);
    }

    public Stream<Dispute> stream() {
        return list.stream();
    }

    public void setAll(Collection<Dispute> collection) {
        this.list.clear();
        this.list.addAll(collection);
    }
}
