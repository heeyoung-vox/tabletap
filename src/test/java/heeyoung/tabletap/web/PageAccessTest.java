package heeyoung.tabletap.web;

import static org.assertj.core.api.Assertions.assertThat;

import heeyoung.tabletap.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PageAccessTest extends IntegrationTest {

    @Test
    void loginAndRegisterPagesArePublic() {
        assertThat(getPage("/login", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getPage("/register", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getPage("/css/app.css", null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void adminPageSendsVisitorsToLogin() {
        // The test client follows the redirect, so the visitor ends up on the login page.
        var response = getPage("/admin/menu", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Log in to manage your restaurant").doesNotContain("Add category");
    }

    @Test
    void staleCookieDoesNotLockVisitorsOutOfPublicPages() {
        String badCookie = "tabletap_token=not.a.token";

        assertThat(getPage("/login", badCookie).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getPage("/register", badCookie).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getPage("/js/api.js", badCookie).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getPage("/admin/menu", badCookie).getBody()).contains("Log in to manage your restaurant");
    }

    @Test
    void adminPageOpensForLoggedInStaff() {
        Account owner = registerRestaurant("Pho Corner");

        var response = getPage("/admin/menu", owner.cookie());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Add category");
    }
}
