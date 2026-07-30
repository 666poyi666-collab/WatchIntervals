package com.poyi.watchintervals.phone;

import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.Assert.assertTrue;

public class PhoneLauncherResourceTest {
    private static final Pattern PATH_DATA = Pattern.compile("android:pathData=\"([^\"]+)\"");
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    @Test public void adaptiveIconsReferenceOriginalColorAndMonochromeLayers() throws Exception {
        Path resources = resources();
        String standard = read(resources.resolve("mipmap-anydpi-v26/ic_launcher.xml"));
        String round = read(resources.resolve("mipmap-anydpi-v26/ic_launcher_round.xml"));
        String foreground = read(resources.resolve("drawable/ic_launcher_foreground.xml"));
        String monochrome = read(resources.resolve("drawable/ic_launcher_monochrome.xml"));

        String monochromeReference = "@drawable/ic_launcher_monochrome";
        assertTrue(standard.contains(monochromeReference));
        assertTrue(round.contains(monochromeReference));
        assertTrue(pathCount(foreground) >= 3);
        assertTrue(pathCount(monochrome) >= 3);
        assertInsideAdaptiveSafeSquare(foreground);
        assertInsideAdaptiveSafeSquare(monochrome);
    }

    private static int pathCount(String xml) {
        int count = 0;
        Matcher matcher = PATH_DATA.matcher(xml);
        while (matcher.find()) count++;
        return count;
    }

    private static void assertInsideAdaptiveSafeSquare(String xml) {
        Matcher paths = PATH_DATA.matcher(xml);
        while (paths.find()) {
            Matcher values = NUMBER.matcher(paths.group(1));
            while (values.find()) {
                double coordinate = Double.parseDouble(values.group());
                assertTrue("adaptive coordinate below safe square: " + coordinate,
                        coordinate >= 21d);
                assertTrue("adaptive coordinate above safe square: " + coordinate,
                        coordinate <= 87d);
            }
        }
    }

    private static Path resources() {
        Path working = Paths.get(System.getProperty("user.dir"));
        Path direct = working.resolve("src/main/res");
        return Files.isDirectory(direct) ? direct : working.resolve("phone/src/main/res");
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
