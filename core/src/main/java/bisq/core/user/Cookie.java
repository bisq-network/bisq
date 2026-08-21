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

package bisq.core.user;

import bisq.common.proto.ProtoUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

/**
 * Serves as flexible container for persisting UI states, layout,...
 * Should not be over-used for domain specific data where type safety and data integrity is important.
 */
public class Cookie extends HashMap<CookieKey, String> {

    public void putAsDouble(CookieKey key, double value) {
        put(key, String.valueOf(value));
    }

    public Optional<Double> getAsOptionalDouble(CookieKey key) {
        try {
            return containsKey(key) ?
                    Optional.of(Double.parseDouble(get(key))) :
                    Optional.empty();
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    public void putAsBoolean(CookieKey key, boolean value) {
        put(key, value ? "1" : "0");
    }

    public Optional<Boolean> getAsOptionalBoolean(CookieKey key) {
        return containsKey(key) ?
                Optional.of(get(key).equals("1")) :
                Optional.empty();
    }

    public Map<String, String> toProtoMessage() {
        Map<String, String> protoMap = new HashMap<>();
        this.forEach((key, value) -> {
            if (key != null) {
                String name = key.name();
                protoMap.put(name, value);
            }
        });
        return protoMap;
    }

    public static Cookie fromProto(@Nullable Map<String, String> protoMap) {
        Cookie cookie = new Cookie();
        if (protoMap != null) {
            protoMap.forEach((key, value) -> cookie.put(ProtoUtil.enumFromProto(CookieKey.class, key), value));
        }
        return cookie;
    }


}
