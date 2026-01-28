package es.buni.hcb.utils;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class Utils {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yy.M.d");

    public static final String BUILD_DATE = getBuildDate();

    private static String getBuildDate() {
        Instant instant = getClassBuildInstant();
        if (instant == null) {
            return "unknown";
        }

        LocalDate date = instant.atZone(ZoneId.systemDefault()).toLocalDate();
        return FORMATTER.format(date);
    }

    private static Instant getClassBuildInstant() {
        try {
            Class<?> clazz = Utils.class;
            URL resource = clazz.getResource(clazz.getSimpleName() + ".class");

            if (resource == null) {
                return null;
            }

            switch (resource.getProtocol()) {
                case "file":
                    return Instant.ofEpochMilli(
                            new File(resource.toURI()).lastModified()
                    );

                case "jar":
                    String path = resource.getPath();
                    File jarFile = new File(path.substring(5, path.indexOf("!")));
                    return Instant.ofEpochMilli(jarFile.lastModified());

                default:
                    return null;
            }
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
    }
}
