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

package bisq.core.monitor;

import lombok.Getter;
import lombok.Setter;


public enum StringValueItem implements ReportingItem {
    Unspecified("", "Unspecified"),

    daoStateHash("dao", "daoStateHash"),
    proposalHash("dao", "proposalHash"),
    blindVoteHash("dao", "blindVoteHash"),

    version("node", "version"),
    commitHash("node", "commitHash");

    @Getter
    @Setter
    private String key;
    @Getter
    @Setter
    private String group;
    @Getter
    @Setter
    private String value;

    StringValueItem(String group, String key) {
        this.group = group;
        this.key = key;
    }

    public StringValueItem withValue(String value) {
        setValue(value);
        return this;
    }

    public static StringValueItem from(String key, String value) {
        StringValueItem item;
        try {
            item = StringValueItem.valueOf(key);
        } catch (Throwable t) {
            item = StringValueItem.Unspecified;
            item.setKey(key);
        }

        item.setValue(value);
        return item;
    }

    @Override
    public String getPath() {
        return group + "." + key;
    }

    @Override
    public protobuf.ReportingItem toProtoMessage() {
        return getBuilder().setStringValueItem(protobuf.StringValueItem.newBuilder()
                        .setValue(value))
                .build();
    }

    public static StringValueItem fromProto(protobuf.ReportingItem baseProto, protobuf.StringValueItem proto) {
        return StringValueItem.from(baseProto.getKey(), proto.getValue());
    }

    @Override
    public String toString() {
        return name() + "=" + value;
    }
}
