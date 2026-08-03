package com.poyi.watchintervals.phone;

import org.junit.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PhoneLauncherResourceTest {
    private static final Pattern PATH_DATA = Pattern.compile("android:pathData=\"([^\"]+)\"");
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final Pattern COMMAND = Pattern.compile("([A-Za-z])([^A-Za-z]*)");
    private static final Pattern COLOR = Pattern.compile("#[0-9A-Fa-f]{6,8}");

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

    @Test public void phoneAndWatchUseTheSameStagedMotionMark() throws Exception {
        Path root = repositoryRoot();
        String phone = read(root.resolve("phone/src/main/res/drawable/ic_launcher_foreground.xml"));
        String watch = read(root.resolve("app/src/main/res/drawable/ic_launcher_foreground.xml"));
        assertEquals("launcher geometry diverged between phone and watch",
                pathData(watch), pathData(phone));
        assertEquals("launcher colors diverged between phone and watch",
                literalColors(watch), literalColors(phone));
        assertEquals("legacy launcher geometry diverged between phone and watch",
                pathData(read(root.resolve("app/src/main/res/drawable/ic_launcher.xml"))),
                pathData(read(root.resolve("phone/src/main/res/drawable/ic_launcher.xml"))));
        assertEquals("monochrome launcher geometry diverged between phone and watch",
                pathData(read(root.resolve("app/src/main/res/drawable/ic_launcher_monochrome.xml"))),
                pathData(read(root.resolve("phone/src/main/res/drawable/ic_launcher_monochrome.xml"))));
        assertEquals("launcher backgrounds diverged between phone and watch",
                literalColors(read(root.resolve("app/src/main/res/values/ic_launcher_background.xml"))),
                literalColors(read(root.resolve("phone/src/main/res/values/ic_launcher_background.xml"))));
        for (String color : new String[]{"#84E66A", "#48CBEA", "#FF4D67"}) {
            assertTrue("phone mark lost " + color, phone.contains(color));
            assertTrue("watch mark lost " + color, watch.contains(color));
        }
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
            Matcher commands = COMMAND.matcher(paths.group(1));
            while (commands.find()) {
                char command = Character.toUpperCase(commands.group(1).charAt(0));
                ArrayList<Double> values = new ArrayList<>();
                Matcher number = NUMBER.matcher(commands.group(2));
                while (number.find()) values.add(Double.parseDouble(number.group()));
                int group = command == 'A' ? 7 : command == 'C' ? 6
                        : command == 'S' || command == 'Q' ? 4 : 2;
                if (command == 'H' || command == 'V') group = 1;
                for (int start = 0; start + group <= values.size(); start += group) {
                    if (command == 'A') {
                        assertCoordinate(values.get(start + 5));
                        assertCoordinate(values.get(start + 6));
                    } else if (command == 'H' || command == 'V') {
                        assertCoordinate(values.get(start));
                    } else {
                        for (int index = start; index < start + group; index++)
                            assertCoordinate(values.get(index));
                    }
                }
            }
        }
    }

    private static void assertCoordinate(double coordinate) {
        assertTrue("adaptive coordinate below safe square: " + coordinate, coordinate >= 21d);
        assertTrue("adaptive coordinate above safe square: " + coordinate, coordinate <= 87d);
    }

    private static List<String> pathData(String xml) {
        ArrayList<String> result = new ArrayList<>();
        Matcher matcher = PATH_DATA.matcher(xml);
        while (matcher.find()) result.add(matcher.group(1).replaceAll("\\s+", " ").trim());
        return result;
    }

    private static List<String> literalColors(String xml) {
        ArrayList<String> result = new ArrayList<>();
        Matcher matcher = COLOR.matcher(xml);
        while (matcher.find()) result.add(matcher.group().toUpperCase());
        return result;
    }

    private static Path resources() {
        Path working = Paths.get(System.getProperty("user.dir"));
        Path direct = working.resolve("src/main/res");
        return Files.isDirectory(direct) ? direct : working.resolve("phone/src/main/res");
    }

    private static Path repositoryRoot() {
        Path working = Paths.get(System.getProperty("user.dir"));
        if (Files.isDirectory(working.resolve("phone")) && Files.isDirectory(working.resolve("app")))
            return working;
        Path parent = working.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("phone"))
                && Files.isDirectory(parent.resolve("app"))) return parent;
        throw new IllegalStateException("repository root unavailable from " + working);
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
