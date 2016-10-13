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

package io.bitsquare.common;

import io.bitsquare.io.LookAheadObjectInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

public class ByteArrayUtils {
    private static final Logger log = LoggerFactory.getLogger(ByteArrayUtils.class);
    private static long lastTimeStamp = System.currentTimeMillis();

    public static <T> T byteArrayToObject(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInput in = null;
        Object result = null;
        try {
            in = new LookAheadObjectInputStream(bis, true);
            result = in.readObject();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
            try {
                bis.close();
            } catch (IOException ex) {
                // ignore close exception
            }
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException ex) {
                // ignore close exception
            }
        }
        return (T) result;
    }

    public static byte[] objectToByteArray(Object object) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutput out = null;
        byte[] result = null;
        try {
            out = new ObjectOutputStream(bos);
            out.writeObject(object);
            result = bos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException ex) {
                // ignore close exception
            }
            try {
                bos.close();
            } catch (IOException ex) {
                // ignore close exception
            }
        }
        return result;
    }
}
