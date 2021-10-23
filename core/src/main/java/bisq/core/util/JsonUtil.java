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

package bisq.core.util;

import bisq.core.offer.bisq_v1.OfferPayload;
import bisq.core.trade.model.bisq_v1.Contract;

import bisq.common.util.JsonExclude;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.GsonBuilder;


public class JsonUtil {
    public static String objectToJson(Object object) {
        GsonBuilder gsonBuilder = new GsonBuilder()
                .setExclusionStrategies(new AnnotationExclusionStrategy())
                .setPrettyPrinting();
        if (object instanceof Contract || object instanceof OfferPayload) {
            gsonBuilder.registerTypeAdapter(OfferPayload.class,
                    new OfferPayload.JsonSerializer());
        }
        return gsonBuilder.create().toJson(object);
    }

    private static class AnnotationExclusionStrategy implements ExclusionStrategy {
        @Override
        public boolean shouldSkipField(FieldAttributes f) {
            return f.getAnnotation(JsonExclude.class) != null;
        }

        @Override
        public boolean shouldSkipClass(Class<?> clazz) {
            return false;
        }
    }
}
