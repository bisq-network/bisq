package com.joemelsha.crypto.hash;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * @author Joseph Robert Melsha (joe.melsha@live.com)
 *
 * Source: https://github.com/jrmelsha/keccak
 * Created: Jun 23, 2016
 *
 * Copyright 2016 Joseph Robert Melsha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class Keccak {
    private static final int MAX_STATE_SIZE = 1600;
    private static final int MAX_STATE_SIZE_WORDS = MAX_STATE_SIZE / 64;

    protected int rateSizeBits, digestSizeBits;
    private long[] state = new long[MAX_STATE_SIZE_WORDS];
    private int rateBits;
    private boolean padded;

    public Keccak(int digestSizeBits) {
        reset(digestSizeBits);
    }

    public Keccak(Keccak other) {
        System.arraycopy(other.state, 0, state, 0, other.state.length);
        rateBits = other.rateBits;
        rateSizeBits = other.rateSizeBits;
        digestSizeBits = other.digestSizeBits;
        padded = other.padded;
    }

    @Override
    public String toString() {
        return "Keccak-" + digestSizeBits;
    }

    public int rateSize() {
        return rateSizeBits >>> 3;
    }

    public int digestSize() {
        return digestSizeBits >>> 3;
    }

    public void reset() {
        reset(rateSizeBits, digestSizeBits);
    }

    protected int rateSizeBitsFor(int digestSizeBits) {
        //@formatter:off
        switch (digestSizeBits) {
            case 288:
                return 1024;
            case 128:
                return 1344;
            case 224:
                return 1152;
            case 256:
                return 1088;
            case 384:
                return 832;
            case 512:
                return 576;
            default:
                throw new IllegalArgumentException("Invalid digestSizeBits: " + digestSizeBits + " ⊄ { 128, 224, 256, 288, 384, 512 }");
        }
        //@formatter:on
    }

    public void reset(int digestSizeBits) {
        reset(rateSizeBitsFor(digestSizeBits), digestSizeBits);
    }

    protected void reset(int rateSizebits, int digestSizeBits) {
        if (rateSizebits + digestSizeBits * 2 != MAX_STATE_SIZE)
            throw new IllegalArgumentException("Invalid rateSizebits + digestSizeBits * 2: " + rateSizebits + " + " + digestSizeBits + " * 2 != " + MAX_STATE_SIZE);
        if (rateSizebits <= 0 || (rateSizebits & 0x3f) > 0)
            throw new IllegalArgumentException("Invalid rateSizebits: " + rateSizebits);

        for (int i = 0; i < MAX_STATE_SIZE_WORDS; ++i)
            state[i] = 0;
        rateBits = 0;

        rateSizeBits = rateSizebits;
        this.digestSizeBits = digestSizeBits;
        padded = false;
    }

    public void update(byte in) {
        updateBits(in & 0xff, 8);
    }

    public void update(byte[] in) {
        update(ByteBuffer.wrap(in));
    }

    public void update(byte[] in, int offset, int length) {
        update(ByteBuffer.wrap(in, offset, length));
    }

    public void update(ByteBuffer in) {
        int inBytes = in.remaining();
        if (inBytes <= 0)
            return;

        if (padded)
            throw new IllegalStateException("Cannot update while padded");

        int rateBits = this.rateBits;
        if ((rateBits & 0x7) > 0) //this could be implemented but would introduce considerable performance degradation - also, it's never technically possible.
            throw new IllegalStateException("Cannot update while in bit-mode");

        long[] state = this.state;
        int rateBytes = rateBits >>> 3;

        int rateBytesWord = rateBytes & 0x7;
        if (rateBytesWord > 0) {
            //logically must have space at this point
            int c = 8 - rateBytesWord;
            if (c > inBytes)
                c = inBytes;
            int i = rateBytes >>> 3;
            long w = state[i];
            rateBytes += c;
            inBytes -= c;
            rateBytesWord <<= 3;
            c = rateBytesWord + (c << 3);
            do {
                w ^= (long) (in.get() & 0xff) << rateBytesWord;
                rateBytesWord += 8;
            } while (rateBytesWord < c);
            state[i] = w;

            if (inBytes <= 0) {
                this.rateBits = rateBytes << 3;
                return;
            }
        }

        int rateWords = rateBytes >>> 3;
        int rateSizeWords = rateSizeBits >>> 6;

        int inWords = inBytes >>> 3;
        if (inWords > 0) {
            ByteOrder order = in.order();
            try {
                in.order(ByteOrder.LITTLE_ENDIAN);
                do {
                    if (rateWords >= rateSizeWords) {
                        keccak(state);
                        rateWords = 0;
                    }
                    int c = rateSizeWords - rateWords;
                    if (c > inWords)
                        c = inWords;
                    inWords -= c;
                    c += rateWords;
                    do {
                        state[rateWords] ^= in.getLong();
                        rateWords++;
                    } while (rateWords < c);
                } while (inWords > 0);
            } finally {
                in.order(order);
            }
            inBytes &= 0x7;
            if (inBytes <= 0) {
                this.rateBits = rateWords << 6;
                return;
            }
        }

        if (rateWords >= rateSizeWords) {
            keccak(state);
            rateWords = 0;
        }
        long w = state[rateWords];
        inBytes <<= 3;
        int i = 0;
        do {
            w ^= (long) (in.get() & 0xff) << i;
            i += 8;
        } while (i < inBytes);
        state[rateWords] = w;

        this.rateBits = (rateWords << 6) | inBytes;
    }

    protected void updateBits(long in, int inBits) {
        if (inBits < 0 || inBits > 64)
            throw new IllegalArgumentException("Invalid valueBits: " + 0 + " < " + inBits + " > " + 64);

        if (inBits <= 0)
            return;

        if (padded)
            throw new IllegalStateException("Cannot update while padded");

        long[] state = this.state;
        int rateBits = this.rateBits;
        int rateBitsWord = rateBits & 0x3f;
        if (rateBitsWord > 0) {
            //logically must have space at this point
            int c = 64 - rateBitsWord;
            if (c > inBits)
                c = inBits;
            state[rateBits >>> 6] ^= (in & (-1L >>> -c)) << rateBitsWord;
            rateBits += c;
            inBits -= c;
            if (inBits <= 0) {
                this.rateBits = rateBits;
                return;
            }
            in >>>= c;
        }
        if (rateBits >= rateSizeBits) {
            Keccak.keccak(state);
            rateBits = 0;
        }
        state[rateBits >>> 6] ^= in & (-1L >>> -inBits);
        this.rateBits = rateBits + inBits;
    }

    public ByteBuffer digest() {
        return digest(digestSize());
    }

    public ByteBuffer digest(int outSize) {
        return digest(outSize, false);
    }

    public ByteBuffer digest(int outSize, boolean direct) {
        ByteBuffer buffer = direct ? ByteBuffer.allocateDirect(outSize) : ByteBuffer.allocate(outSize);
        digest(buffer);
        buffer.flip();
        return buffer;
    }

    public byte[] digestArray() {
        return digestArray(digestSize());
    }

    public byte[] digestArray(int outSize) {
        byte[] array = new byte[outSize];
        digest(array, 0, outSize);
        return array;
    }

    public void digest(byte[] out) {
        digest(ByteBuffer.wrap(out));
    }

    public void digest(byte[] out, int offset, int length) {
        digest(ByteBuffer.wrap(out, offset, length));
    }

    public void digest(ByteBuffer out) {
        int outBytes = out.remaining();
        if (outBytes <= 0)
            return;

        long[] state = this.state;
        int rateBits = this.rateBits;
        int rateBytes;
        if (!padded) {
            pad();
            padded = true;
            rateBits = 0;
            rateBytes = 0;
        } else {
            if ((rateBits & 0x7) > 0)
                throw new IllegalStateException("Cannot digest while in bit-mode"); //this could be implemented but would introduce considerable performance degradation - also, it's never technically possible.

            rateBytes = rateBits >>> 3;
            int rateBytesWord = rateBytes & 0x7;
            if (rateBytesWord > 0) {
                int c = 8 - rateBytesWord;
                if (c > outBytes)
                    c = outBytes;
                long w = state[rateBytes >>> 3];
                outBytes -= c;
                rateBytes += c;
                rateBytesWord <<= 3;
                c = (c << 3) + rateBytesWord;
                do {
                    out.put((byte) (w >>> rateBytesWord));
                    rateBytesWord += 8;
                } while (rateBytesWord < c);
                if (outBytes <= 0) {
                    this.rateBits = rateBytes << 3;
                    return;
                }
            }
        }

        int rateSizeWords = rateSizeBits >>> 6;
        int rateWords = rateBytes >>> 3;

        int outWords = outBytes >>> 3;
        if (outWords > 0) {
            ByteOrder order = out.order();
            try {
                out.order(ByteOrder.LITTLE_ENDIAN);
                do {
                    if (rateWords >= rateSizeWords) {
                        squeeze();
                        rateWords = 0;
                    }
                    int c = rateSizeWords - rateWords;
                    if (c > outWords)
                        c = outWords;
                    outWords -= c;
                    c += rateWords;
                    do {
                        out.putLong(state[rateWords]);
                        rateWords++;
                    } while (rateWords < c);
                } while (outWords > 0);
            } finally {
                out.order(order);
            }
            outBytes &= 0x7;
            if (outBytes <= 0) {
                this.rateBits = rateWords << 6;
                return;
            }
        }

        if (rateWords >= rateSizeWords) {
            squeeze();
            rateWords = 0;
        }
        long w = state[rateWords];
        outBytes <<= 3;
        int i = 0;
        do {
            out.put((byte) (w >>> i));
            i += 8;
        } while (i < outBytes);
        this.rateBits = (rateWords << 6) | outBytes;
    }

    protected void squeeze() {
        Keccak.keccak(state);
    }

    protected void pad() {
        updateBits(0x1, 1);
        if (rateBits >= rateSizeBits) {
            Keccak.keccak(state);
            rateBits = 0;
        }
        rateBits = rateSizeBits - 1;
        updateBits(0x1, 1);
        Keccak.keccak(state);
    }

    private static void keccak(long[] a) {
        //@formatter:off
        int c, i;
        long x, a_10_;
        long x0, x1, x2, x3, x4;
        long t0, t1, t2, t3, t4;
        long c0, c1, c2, c3, c4;
        long[] rc = RC;

        i = 0;
        do {
            //theta (precalculation part)
            c0 = a[0] ^ a[5 + 0] ^ a[10 + 0] ^ a[15 + 0] ^ a[20 + 0];
            c1 = a[1] ^ a[5 + 1] ^ a[10 + 1] ^ a[15 + 1] ^ a[20 + 1];
            c2 = a[2] ^ a[5 + 2] ^ a[10 + 2] ^ a[15 + 2] ^ a[20 + 2];
            c3 = a[3] ^ a[5 + 3] ^ a[10 + 3] ^ a[15 + 3] ^ a[20 + 3];
            c4 = a[4] ^ a[5 + 4] ^ a[10 + 4] ^ a[15 + 4] ^ a[20 + 4];

            t0 = (c0 << 1) ^ (c0 >>> (64 - 1)) ^ c3;
            t1 = (c1 << 1) ^ (c1 >>> (64 - 1)) ^ c4;
            t2 = (c2 << 1) ^ (c2 >>> (64 - 1)) ^ c0;
            t3 = (c3 << 1) ^ (c3 >>> (64 - 1)) ^ c1;
            t4 = (c4 << 1) ^ (c4 >>> (64 - 1)) ^ c2;

            //theta (xorring part) + rho + pi
            a[0] ^= t1;
            x = a[1] ^ t2;
            a_10_ = (x << 1) | (x >>> (64 - 1));
            x = a[6] ^ t2;
            a[1] = (x << 44) | (x >>> (64 - 44));
            x = a[9] ^ t0;
            a[6] = (x << 20) | (x >>> (64 - 20));
            x = a[22] ^ t3;
            a[9] = (x << 61) | (x >>> (64 - 61));

            x = a[14] ^ t0;
            a[22] = (x << 39) | (x >>> (64 - 39));
            x = a[20] ^ t1;
            a[14] = (x << 18) | (x >>> (64 - 18));
            x = a[2] ^ t3;
            a[20] = (x << 62) | (x >>> (64 - 62));
            x = a[12] ^ t3;
            a[2] = (x << 43) | (x >>> (64 - 43));
            x = a[13] ^ t4;
            a[12] = (x << 25) | (x >>> (64 - 25));

            x = a[19] ^ t0;
            a[13] = (x << 8) | (x >>> (64 - 8));
            x = a[23] ^ t4;
            a[19] = (x << 56) | (x >>> (64 - 56));
            x = a[15] ^ t1;
            a[23] = (x << 41) | (x >>> (64 - 41));
            x = a[4] ^ t0;
            a[15] = (x << 27) | (x >>> (64 - 27));
            x = a[24] ^ t0;
            a[4] = (x << 14) | (x >>> (64 - 14));

            x = a[21] ^ t2;
            a[24] = (x << 2) | (x >>> (64 - 2));
            x = a[8] ^ t4;
            a[21] = (x << 55) | (x >>> (64 - 55));
            x = a[16] ^ t2;
            a[8] = (x << 45) | (x >>> (64 - 45));
            x = a[5] ^ t1;
            a[16] = (x << 36) | (x >>> (64 - 36));
            x = a[3] ^ t4;
            a[5] = (x << 28) | (x >>> (64 - 28));

            x = a[18] ^ t4;
            a[3] = (x << 21) | (x >>> (64 - 21));
            x = a[17] ^ t3;
            a[18] = (x << 15) | (x >>> (64 - 15));
            x = a[11] ^ t2;
            a[17] = (x << 10) | (x >>> (64 - 10));
            x = a[7] ^ t3;
            a[11] = (x << 6) | (x >>> (64 - 6));
            x = a[10] ^ t1;
            a[7] = (x << 3) | (x >>> (64 - 3));
            a[10] = a_10_;

            //chi
            c = 0;
            do {
                x0 = a[c + 0];
                x1 = a[c + 1];
                x2 = a[c + 2];
                x3 = a[c + 3];
                x4 = a[c + 4];
                a[c + 0] = x0 ^ ((~x1) & x2);
                a[c + 1] = x1 ^ ((~x2) & x3);
                a[c + 2] = x2 ^ ((~x3) & x4);
                a[c + 3] = x3 ^ ((~x4) & x0);
                a[c + 4] = x4 ^ ((~x0) & x1);

                c += 5;
            } while (c < 25);

            //iota
            a[0] ^= rc[i];

            i++;
        } while (i < 24);
        //@formatter:on
    }

    private static final long[] RC = {0x0000000000000001L, 0x0000000000008082L, 0x800000000000808AL, 0x8000000080008000L, 0x000000000000808BL,
            0x0000000080000001L, 0x8000000080008081L, 0x8000000000008009L, 0x000000000000008AL, 0x0000000000000088L,
            0x0000000080008009L, 0x000000008000000AL, 0x000000008000808BL, 0x800000000000008BL, 0x8000000000008089L,
            0x8000000000008003L, 0x8000000000008002L, 0x8000000000000080L, 0x000000000000800AL, 0x800000008000000AL,
            0x8000000080008081L, 0x8000000000008080L, 0x0000000080000001L, 0x8000000080008008L};
}
