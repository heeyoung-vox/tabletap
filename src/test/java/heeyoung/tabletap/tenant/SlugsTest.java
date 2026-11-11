package heeyoung.tabletap.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SlugsTest {

    @Test
    void lowercasesAndJoinsWordsWithHyphens() {
        assertThat(Slugs.slugify("  Pho Corner & Grill ")).isEqualTo("pho-corner-grill");
    }

    @Test
    void stripsVietnameseDiacritics() {
        assertThat(Slugs.slugify("Phở Đặc Biệt 24/7")).isEqualTo("pho-dac-biet-24-7");
    }

    @Test
    void fallsBackWhenNothingUsableRemains() {
        assertThat(Slugs.slugify("!!!")).isEqualTo("restaurant");
    }

    @Test
    void truncatesLongNamesWithoutATrailingHyphen() {
        String slug = Slugs.slugify("a".repeat(49) + " " + "b".repeat(30));

        assertThat(slug).isEqualTo("a".repeat(49));
    }
}
