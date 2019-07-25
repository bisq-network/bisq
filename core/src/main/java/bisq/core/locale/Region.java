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

package bisq.core.locale;

import bisq.common.proto.persistable.PersistablePayload;

import com.google.protobuf.Message;

import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.annotation.concurrent.Immutable;

@Immutable
@EqualsAndHashCode
@ToString
public final class Region implements PersistablePayload {
    public final String code;
    public final String name;

    public Region(String code, String name) {
        this.code = code;
        this.name = name;
    }

    @Override
    public Message toProtoMessage() {
        return protobuf.Region.newBuilder().setCode(code).setName(name).build();
    }

    public static Region fromProto(protobuf.Region proto) {
        return new Region(proto.getCode(), proto.getName());
    }
}
