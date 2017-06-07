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

package io.bisq.common.proto.persistable;

import com.google.common.collect.Lists;
import com.google.protobuf.Message;
import io.bisq.generated.protobuffer.PB;
import lombok.*;
import org.springframework.util.CollectionUtils;

import java.util.List;

@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class PersistableViewPath implements PersistableEnvelope {
    private List<String> viewPath = Lists.newArrayList();

    @Override
    public Message toProtoMessage() {
        final PB.ViewPathAsString.Builder builder = PB.ViewPathAsString.newBuilder();
        if (!CollectionUtils.isEmpty(viewPath))
            builder.addAllViewPath(viewPath);
        return PB.PersistableEnvelope.newBuilder().setViewPathAsString(builder).build();
    }

    public static PersistableEnvelope fromProto(PB.ViewPathAsString proto) {
        return new PersistableViewPath(proto.getViewPathList());
    }
}
