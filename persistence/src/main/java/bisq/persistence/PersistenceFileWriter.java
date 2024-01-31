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

package bisq.persistence;

public class PersistenceFileWriter {
    public interface AsyncFileWriter {
        int write(byte[] data, int offset);
    }

    public PersistenceFileWriter(AsyncFileWriter asyncWriter) {
        this.asyncWriter = asyncWriter;
    }

    private final AsyncFileWriter asyncWriter;

    public boolean write(byte[] data) {
        int totalWrittenBytes = asyncWriter.write(data, 0);
        if (totalWrittenBytes == data.length) {
            return true;
        }

        int remainingBytes = data.length - totalWrittenBytes;
        while (remainingBytes > 0) {
            int writtenBytes = asyncWriter.write(data, totalWrittenBytes);
            totalWrittenBytes += writtenBytes;
            remainingBytes = data.length - totalWrittenBytes;
        }

        return true;
    }
}
