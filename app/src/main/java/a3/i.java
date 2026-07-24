package a3;

import com.baidu.platform.comjni.map.basemap.NABaseMap;
import java.lang.reflect.Method;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * Minimal replacement for the R8 synthetic string helper that the map SDK
 * inherited from the system Sports APK. Keeping this class local avoids
 * packaging unrelated, obfuscated Sports application classes.
 */
public final class i {
    private i() {}

    public static String g(String value, int number) {
        return value + number;
    }

    public static String j(String first, String second) {
        return first + second;
    }

    public static String k(String first, String second, String third) {
        return first + second + third;
    }

    public static String l(StringBuilder builder, int value, char suffix) {
        return builder.append(value).append(suffix).toString();
    }

    public static String m(StringBuilder builder, int value, String suffix) {
        return builder.append(value).append(suffix).toString();
    }

    public static String n(StringBuilder builder, String value, String suffix) {
        return builder.append(value).append(suffix).toString();
    }

    public static StringBuilder o(String value) {
        return new StringBuilder().append(value);
    }

    public static StringBuilder q(
            String first, int firstNumber, String middle, int secondNumber, String suffix) {
        return new StringBuilder()
                .append(first)
                .append(firstNumber)
                .append(middle)
                .append(secondNumber)
                .append(suffix);
    }

    public static StringBuilder r(String first, String second) {
        return new StringBuilder().append(first).append(second);
    }

    public static void u(NABaseMap map) {
        // The accessor is ACC_SYNTHETIC, so javac deliberately hides it from
        // source-level overload resolution although the SDK bytecode calls it.
        try {
            Method accessor = NABaseMap.class.getDeclaredMethod("a", NABaseMap.class);
            accessor.setAccessible(true);
            ReadWriteLock lock = (ReadWriteLock) accessor.invoke(null, map);
            lock.readLock().unlock();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Baidu map lock accessor unavailable", error);
        }
    }

    public static void y(StringBuilder builder, String value, char separator, String suffix) {
        builder.append(value).append(separator).append(suffix);
    }
}
