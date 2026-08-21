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

package bisq.core.support.dispute.messages;

import bisq.core.support.SupportType;
import bisq.core.support.messages.SupportMessage;

import bisq.common.crypto.Sig;
import bisq.common.proto.ProtoUtil;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import java.security.PublicKey;

import java.util.Date;
import java.util.GregorianCalendar;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

public abstract class DisputeMessage extends SupportMessage {
    public static final long TTL = TimeUnit.DAYS.toMillis(15);
    // Compatibility window for dispute messages sent by clients before sender_signature_pub_key existed.
    // TODO: Once no old dispute messages are expected, remove the null handling/nullability annotations and enforce unconditionally.
    public static final Date SENDER_SIGNATURE_PUB_KEY_VALIDATION_ACTIVATION_DATE =
            Utilities.getUTCDate(2026, GregorianCalendar.SEPTEMBER, 1);

    public DisputeMessage(int messageVersion, String uid, SupportType supportType) {
        super(messageVersion, uid, supportType);
    }

    @Override
    public long getTTL() {
        return TTL;
    }

    protected static boolean isSenderSignaturePubKeyValidationRequired(@Nullable Date tradeDate) {
        return isSenderSignaturePubKeyValidationRequired(new Date(), tradeDate);
    }

    static boolean isSenderSignaturePubKeyValidationRequired(Date now, @Nullable Date tradeDate) {
        if (!now.after(SENDER_SIGNATURE_PUB_KEY_VALIDATION_ACTIVATION_DATE)) {
            return false;
        }

        // Proto3 uses 0 as the default for unset int64 fields, so treat epoch/non-positive dates as missing.
        if (tradeDate == null || tradeDate.getTime() <= 0L) {
            return true;
        }

        return !tradeDate.before(SENDER_SIGNATURE_PUB_KEY_VALIDATION_ACTIVATION_DATE);
    }

    @Nullable
    protected static PublicKey senderSignaturePubKeyFromProto(ByteString proto) {
        byte[] publicKeyBytes = ProtoUtil.byteArrayOrNullFromProto(proto);
        return publicKeyBytes == null ? null : Sig.getPublicKeyFromBytes(publicKeyBytes);
    }

    protected static ByteString senderSignaturePubKeyToProto(PublicKey senderSignaturePubKey) {
        return ByteString.copyFrom(Sig.getPublicKeyBytes(senderSignaturePubKey));
    }
}
