package me.cortex.voxy.common.util;

public final class BitMath {
    private static final int BIOME_MASK = 0xCCC;
    private static final int FACE_MASK = 0x7C1F;
    private static final long COUNTER_MASK = 0x4210842108421L;
    private static final int[] BIOME_COMPRESS = new int[4096];
    private static final int[] FACE_EXPAND = new int[1024];
    private static final long[] COUNTER_EXPAND = new long[2048];

    static {
        for (int i = 0; i < BIOME_COMPRESS.length; i++) {
            BIOME_COMPRESS[i] = compressSlow(i, BIOME_MASK);
        }
        for (int i = 0; i < FACE_EXPAND.length; i++) {
            FACE_EXPAND[i] = expandSlow(i, FACE_MASK);
        }
        for (int i = 0; i < COUNTER_EXPAND.length; i++) {
            COUNTER_EXPAND[i] = expandSlow(i, COUNTER_MASK);
        }
    }

    private BitMath() {
    }

    public static int compress(int value, int mask) {
        if (mask == BIOME_MASK) {
            return BIOME_COMPRESS[value & 0xFFF];
        }
        return compressSlow(value, mask);
    }

    public static int expand(int value, int mask) {
        if (mask == FACE_MASK) {
            return FACE_EXPAND[value & 0x3FF];
        }
        return expandSlow(value, mask);
    }

    public static long expand(long value, long mask) {
        if (mask == COUNTER_MASK) {
            return COUNTER_EXPAND[(int) value & 0x7FF];
        }
        return expandSlow(value, mask);
    }

    private static int compressSlow(int value, int mask) {
        int result = 0;
        int outputBit = 1;
        while (mask != 0) {
            int selectedBit = mask & -mask;
            if ((value & selectedBit) != 0) {
                result |= outputBit;
            }
            mask ^= selectedBit;
            outputBit <<= 1;
        }
        return result;
    }

    private static int expandSlow(int value, int mask) {
        int result = 0;
        int inputBit = 1;
        while (mask != 0) {
            int selectedBit = mask & -mask;
            if ((value & inputBit) != 0) {
                result |= selectedBit;
            }
            mask ^= selectedBit;
            inputBit <<= 1;
        }
        return result;
    }

    private static long expandSlow(long value, long mask) {
        long result = 0;
        long inputBit = 1;
        while (mask != 0) {
            long selectedBit = mask & -mask;
            if ((value & inputBit) != 0) {
                result |= selectedBit;
            }
            mask ^= selectedBit;
            inputBit <<= 1;
        }
        return result;
    }
}
