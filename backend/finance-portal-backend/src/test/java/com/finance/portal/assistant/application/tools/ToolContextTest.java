package com.finance.portal.assistant.application.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ToolContext} record — auth kontrolü ({@code isAuthenticated}) ve accessor'lar.
 * Portföy/alarm/favori write-araçları bu kontrole dayanır; tüm dallar test edilir.
 */
class ToolContextTest {

    @Test
    @DisplayName("isAuthenticated: dolu userId → true; accessor'lar değerleri döndürür")
    void authenticated_whenUserIdPresent() {
        ToolContext ctx = new ToolContext("u1", "Berkan", "berkan@example.com");

        assertThat(ctx.isAuthenticated()).isTrue();
        assertThat(ctx.userId()).isEqualTo("u1");
        assertThat(ctx.userName()).isEqualTo("Berkan");
        assertThat(ctx.userEmail()).isEqualTo("berkan@example.com");
    }

    @Test
    @DisplayName("isAuthenticated: null userId → false (anonim)")
    void notAuthenticated_whenUserIdNull() {
        ToolContext ctx = new ToolContext(null, null, null);

        assertThat(ctx.isAuthenticated()).isFalse();
        assertThat(ctx.userId()).isNull();
        assertThat(ctx.userName()).isNull();
        assertThat(ctx.userEmail()).isNull();
    }

    @ParameterizedTest(name = "isAuthenticated: userId=[{0}] → false (boş/whitespace)")
    @ValueSource(strings = {"", " ", "   ", "\t"})
    @DisplayName("isAuthenticated: boş ya da yalnızca boşluk userId → false")
    void notAuthenticated_whenUserIdBlank(String blankId) {
        ToolContext ctx = new ToolContext(blankId, "Ada", "ada@example.com");

        assertThat(ctx.isAuthenticated()).isFalse();
        // userName/userEmail dolu olsa bile auth FALSE — yalnız userId belirleyici.
        assertThat(ctx.userName()).isEqualTo("Ada");
    }

    @Test
    @DisplayName("isAuthenticated: userId dolu ama isim/email null olabilir → yine de true")
    void authenticated_evenIfNameAndEmailNull() {
        ToolContext ctx = new ToolContext("u2", null, null);

        assertThat(ctx.isAuthenticated()).isTrue();
        assertThat(ctx.userName()).isNull();
        assertThat(ctx.userEmail()).isNull();
    }

    @Test
    @DisplayName("record eşitliği/hashCode/toString — record sözleşmesi")
    void recordContract() {
        ToolContext a = new ToolContext("u1", "Berkan", "b@x.com");
        ToolContext b = new ToolContext("u1", "Berkan", "b@x.com");
        ToolContext c = new ToolContext("u9", "Berkan", "b@x.com");

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo(c);
        assertThat(a.toString()).contains("u1").contains("Berkan");
    }
}
