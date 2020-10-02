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

import bisq.common.proto.persistable.PersistableList;
import bisq.common.proto.persistable.PersistablePayload;

import javafx.collections.FXCollections;

import java.util.List;

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
public abstract class DisputeList<T extends PersistablePayload> extends PersistableList<T> {
    protected List<T> createList() {
        return FXCollections.observableArrayList();
    }

    public DisputeList() {
    }

    protected DisputeList(List<T> list) {
        super(list);
    }
}
