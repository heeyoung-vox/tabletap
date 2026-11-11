package heeyoung.tabletap.tenant;

import java.text.Normalizer;
import java.util.Locale;

public final class Slugs {

    private static final int MAX_LENGTH = 50;

    private Slugs() {
    }

    public static String slugify(String name) {
        // NFD does not decompose the Vietnamese letter d-with-stroke, so map it by hand.
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');
        String slug = ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "restaurant" : slug;
    }
}
