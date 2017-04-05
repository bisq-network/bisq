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

package io.bisq.common.locale;

import com.google.protobuf.Message;
import io.bisq.common.app.Version;
import io.bisq.common.persistance.Persistable;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.annotation.concurrent.Immutable;

@Immutable
@EqualsAndHashCode
@ToString
public final class Country implements Persistable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    public final String code;
    public final String name;
    public final Region region;

    public Country(String code, String name, Region region) {
        this.code = code;
        this.name = name;
        this.region = region;
    }

    @Override
    public Message toProtobuf() {
        return PB.Country.newBuilder().setCode(code).setName(name)
                .setRegion(PB.Region.newBuilder().setCode(region.code).setName(region.name)).build();
    }
}
