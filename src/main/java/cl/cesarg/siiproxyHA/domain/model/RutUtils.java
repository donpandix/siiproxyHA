package cl.cesarg.siiproxyHA.domain.model;

import java.util.Locale;

public final class RutUtils {

    private RutUtils() {}

    public static String normalizeAndValidate(String value, String fieldName) {
        String normalized = normalize(value);
        if (!isValid(normalized)) {
            throw new IllegalArgumentException(fieldName + " is invalid");
        }
        return normalized;
    }

    public static String normalize(String value) {
        if (value == null) return "";
        String compact = value.trim()
                .toUpperCase(Locale.ROOT)
                .replace(".", "")
                .replace("-", "")
                .replace(" ", "");
        if (compact.length() < 2) return compact;
        return compact.substring(0, compact.length() - 1) + "-" + compact.charAt(compact.length() - 1);
    }

    public static boolean isValid(String normalized) {
        if (normalized == null || !normalized.matches("[0-9]{1,8}-[0-9K]")) return false;

        String[] parts = normalized.split("-");
        int factor = 2;
        int sum = 0;
        for (int index = parts[0].length() - 1; index >= 0; index--) {
            sum += Character.digit(parts[0].charAt(index), 10) * factor;
            factor = factor == 7 ? 2 : factor + 1;
        }

        int result = 11 - (sum % 11);
        char expected = result == 11 ? '0' : result == 10 ? 'K' : Character.forDigit(result, 10);
        return expected == parts[1].charAt(0);
    }
}
