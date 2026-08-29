package me.cortex.voxy.common.util;

/** Java 17 equivalents of the bit compress/expand helpers added to newer JDKs. */
public final class BitUtil {
    private BitUtil() {}

    public static int compress(int value, int mask) {
        int result = 0;
        int outputBit = 1;
        for (int bit = 1; bit != 0; bit <<= 1) {
            if ((mask & bit) != 0) {
                if ((value & bit) != 0) result |= outputBit;
                outputBit <<= 1;
            }
        }
        return result;
    }

    public static int expand(int value, int mask) {
        int result = 0;
        int inputBit = 1;
        for (int bit = 1; bit != 0; bit <<= 1) {
            if ((mask & bit) != 0) {
                if ((value & inputBit) != 0) result |= bit;
                inputBit <<= 1;
            }
        }
        return result;
    }

    public static long expand(long value, long mask) {
        long result = 0;
        long inputBit = 1;
        for (long bit = 1; bit != 0; bit <<= 1) {
            if ((mask & bit) != 0) {
                if ((value & inputBit) != 0) result |= bit;
                inputBit <<= 1;
            }
        }
        return result;
    }
}
