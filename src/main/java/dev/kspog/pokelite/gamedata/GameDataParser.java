package dev.kspog.pokelite.gamedata;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GameDataParser {
    private static final Pattern NUMBER = Pattern.compile("(\\d[\\d,. ]*)");

    private GameDataParser() {
    }

    public static Long parseMoney(String text) {
        List<Long> values = parseNumbers(text);
        return values.isEmpty() ? null : values.get(0);
    }

    public static ExperienceValue parseExperience(String text) {
        List<Long> values = parseNumbers(text);
        if (values.isEmpty()) {
            return null;
        }

        String normalized = text.toLowerCase(Locale.ROOT);
        if (normalized.contains("exp") && normalized.contains("+")) {
            return new ExperienceValue(values.get(0), null);
        }

        Long required = values.size() >= 2 ? values.get(1) : null;
        return new ExperienceValue(values.get(0), required);
    }

    private static List<Long> parseNumbers(String text) {
        List<Long> values = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return values;
        }

        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            String digits = matcher.group(1).replaceAll("\\D", "");
            if (digits.isEmpty()) {
                continue;
            }
            try {
                values.add(Long.parseLong(digits));
            } catch (NumberFormatException ignored) {
                // Ignore OCR fragments that exceed the range of a signed long.
            }
        }
        return values;
    }
}
