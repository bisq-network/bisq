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

package bisq.core.dao.state.model.governance;

import bisq.core.dao.state.model.ImmutableDaoStateModel;

import bisq.common.proto.persistable.PersistablePayload;

import lombok.Value;

import javax.annotation.concurrent.Immutable;

/**
 * Holds the data for a parameter change. Gets persisted with the DaoState.
 */
@Immutable
@Value
public class ParamChange implements PersistablePayload, ImmutableDaoStateModel {
    // We use the enum name instead of the enum to be more flexible with changes at updates
    private final String paramName;
    private final String value;
    private final int activationHeight;

    public ParamChange(String paramName, String value, int activationHeight) {
        this.paramName = paramName;
        this.value = value;
        this.activationHeight = activationHeight;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Override
    public protobuf.ParamChange toProtoMessage() {
        return protobuf.ParamChange.newBuilder()
                .setParamName(paramName)
                .setParamValue(value)
                .setActivationHeight(activationHeight)
                .build();
    }

    public static ParamChange fromProto(protobuf.ParamChange proto) {
        return new ParamChange(proto.getParamName(),
                proto.getParamValue(),
                proto.getActivationHeight());
    }
}
