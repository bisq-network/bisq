/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.util;

import org.bitcoinj.core.Utils;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Bytes {
    private static final Logger log = LoggerFactory.getLogger(Bytes.class);

    public final byte[] bytes;
    public final String string;

    public Bytes(byte[] bytes) {
        this.bytes = Arrays.copyOf(bytes, bytes.length);
        this.string = Utils.HEX.encode(bytes);
    }

    public Bytes(String string) {
        this.string = string;
        this.bytes = Utils.HEX.decode(string);
    }

    @Override
    public String toString() {
        return string;
    }

    public static class GsonAdapter extends TypeAdapter<Bytes> {
        @Override
        public Bytes read(JsonReader reader) throws IOException {
            return new Bytes(reader.nextString());
        }

        @Override
        public void write(JsonWriter out, Bytes value) throws IOException {
            if (value == null)
                out.nullValue();
            else
                out.value(value.string);
        }
    }
}
