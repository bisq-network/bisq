/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.proto;

import com.google.common.collect.Lists;
import com.google.protobuf.Message;
import io.bisq.common.persistable.PersistableEnvelope;
import io.bisq.generated.protobuffer.PB;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.util.CollectionUtils;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
public class ViewPathAsString implements PersistableEnvelope {
    @Getter
    @Setter
    List<String> viewPath = Lists.newArrayList();

    @Override
    public Message toProtoMessage() {
        return CollectionUtils.isEmpty(viewPath) ? PB.PersistableEnvelope.newBuilder().setViewPathAsString(PB.ViewPathAsString.newBuilder()).build()
                : PB.PersistableEnvelope.newBuilder().setViewPathAsString(PB.ViewPathAsString.newBuilder().addAllViewPath(viewPath)).build();
    }

    public static PersistableEnvelope fromProto(PB.ViewPathAsString viewPathAsString) {
        return new ViewPathAsString(viewPathAsString.getViewPathList());
    }
}
