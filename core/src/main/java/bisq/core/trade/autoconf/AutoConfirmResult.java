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

package bisq.core.trade.autoconf;

import bisq.core.trade.autoconf.xmr.XmrAutoConfirmResult;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import javax.annotation.Nullable;

/**
 * Base class for AutoConfirm implementations
 */
@EqualsAndHashCode
@Getter
public abstract class AutoConfirmResult {

    public static AutoConfirmResult fromCurrencyCode(String currencyCode) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (currencyCode) {
            case "XMR":
                return new XmrAutoConfirmResult();
            default:
                return null;
        }
    }

    private final String stateName;

    protected AutoConfirmResult(String stateName) {
        this.stateName = stateName;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We use fromProto as kind of factory method to get the specific AutoConfirmResult
    @Nullable
    public static AutoConfirmResult fromProto(protobuf.AutoConfirmResult proto, String currencyCode) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (currencyCode) {
            case "XMR":
                return XmrAutoConfirmResult.fromProto(proto);
            default:
                return null;
        }
    }

    public abstract protobuf.AutoConfirmResult toProtoMessage();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    abstract public boolean isSuccessState();

    abstract public String getTextStatus();
}
