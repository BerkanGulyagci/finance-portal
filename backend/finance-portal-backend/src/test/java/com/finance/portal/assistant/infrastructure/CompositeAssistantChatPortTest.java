package com.finance.portal.assistant.infrastructure;

import com.finance.portal.assistant.application.AssistantProperties;
import com.finance.portal.assistant.application.model.ChatMessage;
import com.finance.portal.assistant.application.port.AssistantChatPort.AssistantUnavailableException;
import com.finance.portal.assistant.application.tools.ToolContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link CompositeAssistantChatPort} branch testleri.
 *
 * <p>Sağlayıcı zinciri (GroqChatClient/GeminiChatClient) ve {@link AssistantProperties}
 * Mockito ile mock'lanır; gerçek HTTP yok. Kapsanan dallar:
 * chain null/boş → legacy; zincir adımı geçersiz (null / ':' yok / model boş) → atla;
 * groq/gemini ilk kullanılabilir kazanır; sağlayıcı kullanılamaz (anahtar yok / kota) → sonraki adım;
 * tüm adımlar hata/atla → last varsa fırlat, yoksa "No provider configured (chain)";
 * legacy: groq ok / groq düşer→gemini fallback / groq düşer & gemini yok→rethrow /
 * groq yok & gemini var / her ikisi de yok.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompositeAssistantChatPortTest {

    @Mock private GroqChatClient groq;
    @Mock private GeminiChatClient gemini;
    @Mock private AssistantProperties props;

    private CompositeAssistantChatPort port;

    private final List<ChatMessage> messages = List.of(ChatMessage.user("Merhaba"));
    private final ToolContext ctx = new ToolContext(null, null, null);

    @BeforeEach
    void setUp() {
        port = new CompositeAssistantChatPort(groq, gemini, props);
    }

    private static List<String> chain(String... steps) {
        return new ArrayList<>(Arrays.asList(steps));
    }

    // ── Zincir tanımlı: ilk kullanılabilir kazanır ────────────────────────────

    @Test
    @DisplayName("chain[groq]: props.isUsable=true → groq.completeWith döner, gemini'ye hiç gidilmez")
    void chainGroqWins() {
        when(props.getChain()).thenReturn(chain("groq:llama-x"));
        when(props.isUsable()).thenReturn(true);
        when(groq.completeWith(eq(messages), eq(ctx), eq("llama-x"))).thenReturn("groq-yanit");

        String out = port.complete(messages, ctx);

        assertThat(out).isEqualTo("groq-yanit");
        verifyNoInteractions(gemini);
    }

    @Test
    @DisplayName("chain[gemini]: gemini.isUsable=true → gemini.completeWith döner")
    void chainGeminiWins() {
        when(props.getChain()).thenReturn(chain("gemini:flash-x"));
        when(gemini.isUsable()).thenReturn(true);
        when(gemini.completeWith(eq(messages), eq(ctx), eq("flash-x"))).thenReturn("gemini-yanit");

        String out = port.complete(messages, ctx);

        assertThat(out).isEqualTo("gemini-yanit");
        verify(groq, never()).completeWith(any(), any(), any());
    }

    // ── Provider büyük/küçük harf + boşluk normalize; model trim ───────────────

    @Test
    @DisplayName("chain['GROQ : model ']: provider lower-case + trim, model trim → groq kazanır")
    void chainProviderNormalizedAndModelTrimmed() {
        when(props.getChain()).thenReturn(chain("  GROQ  :  llama-trim  "));
        when(props.isUsable()).thenReturn(true);
        when(groq.completeWith(eq(messages), eq(ctx), eq("llama-trim"))).thenReturn("ok");

        String out = port.complete(messages, ctx);

        assertThat(out).isEqualTo("ok");
    }

    // ── Geçersiz zincir adımları → atlanır ────────────────────────────────────

    @Test
    @DisplayName("chain[null, 'noColon', 'groq:', 'gemini:flash']: geçersizler atlanır, son geçerli kazanır")
    void chainSkipsInvalidSteps() {
        // null adım, ':' içermeyen adım, modeli boş adım → hepsi atlanır; son adım gemini kazanır.
        when(props.getChain()).thenReturn(chain(null, "noColon", "groq:", "gemini:flash"));
        when(gemini.isUsable()).thenReturn(true);
        when(gemini.completeWith(eq(messages), eq(ctx), eq("flash"))).thenReturn("son-adim");

        String out = port.complete(messages, ctx);

        assertThat(out).isEqualTo("son-adim");
    }

    @Test
    @DisplayName("chain['unknown:model']: bilinmeyen sağlayıcı → atlanır, last null → 'No provider configured (chain)'")
    void chainUnknownProviderThenNoneConfigured() {
        when(props.getChain()).thenReturn(chain("openai:gpt"));

        assertThatThrownBy(() -> port.complete(messages, ctx))
                .isInstanceOf(AssistantUnavailableException.class)
                .hasMessageContaining("No assistant provider configured (chain)");

        verifyNoInteractions(groq, gemini);
    }

    @Test
    @DisplayName("chain[groq:m]: props.isUsable=false (anahtar yok) → adım atla, last null → chain hatası")
    void chainGroqNotUsableSkippedNoneConfigured() {
        when(props.getChain()).thenReturn(chain("groq:m"));
        when(props.isUsable()).thenReturn(false);

        assertThatThrownBy(() -> port.complete(messages, ctx))
                .isInstanceOf(AssistantUnavailableException.class)
                .hasMessageContaining("No assistant provider configured (chain)");

        verify(groq, never()).completeWith(any(), any(), any());
    }

    // ── Kota/hata → sonraki adıma düş ─────────────────────────────────────────

    @Test
    @DisplayName("chain[groq, gemini]: groq AssistantUnavailable fırlatır → gemini kazanır")
    void chainGroqFailsFallsToGemini() {
        when(props.getChain()).thenReturn(chain("groq:llama", "gemini:flash"));
        when(props.isUsable()).thenReturn(true);
        when(groq.completeWith(eq(messages), eq(ctx), eq("llama")))
                .thenThrow(new AssistantUnavailableException("429 quota"));
        when(gemini.isUsable()).thenReturn(true);
        when(gemini.completeWith(eq(messages), eq(ctx), eq("flash"))).thenReturn("gemini-ok");

        String out = port.complete(messages, ctx);

        assertThat(out).isEqualTo("gemini-ok");
    }

    @Test
    @DisplayName("chain[groq, gemini]: ikisi de fırlatır → son yakalanan (last) hata yeniden fırlatılır")
    void chainAllFailRethrowsLast() {
        when(props.getChain()).thenReturn(chain("groq:llama", "gemini:flash"));
        when(props.isUsable()).thenReturn(true);
        when(groq.completeWith(eq(messages), eq(ctx), eq("llama")))
                .thenThrow(new AssistantUnavailableException("groq down"));
        when(gemini.isUsable()).thenReturn(true);
        when(gemini.completeWith(eq(messages), eq(ctx), eq("flash")))
                .thenThrow(new AssistantUnavailableException("gemini 429"));

        assertThatThrownBy(() -> port.complete(messages, ctx))
                .isInstanceOf(AssistantUnavailableException.class)
                .hasMessage("gemini 429");
    }

    // ── Legacy yol (chain null / boş) ─────────────────────────────────────────

    @Test
    @DisplayName("legacy: chain null + props.isUsable=true → groq.complete döner")
    void legacyChainNullGroqOk() {
        when(props.getChain()).thenReturn(null);
        when(props.isUsable()).thenReturn(true);
        when(groq.complete(messages, ctx)).thenReturn("legacy-groq");

        String out = port.complete(messages, ctx);

        assertThat(out).isEqualTo("legacy-groq");
        verify(gemini, never()).complete(any(), any());
    }

    @Test
    @DisplayName("legacy: chain boş + groq düşer & gemini.isUsable=true → gemini fallback döner")
    void legacyEmptyChainGroqFailsGeminiFallback() {
        when(props.getChain()).thenReturn(chain()); // boş liste
        when(props.isUsable()).thenReturn(true);
        when(groq.complete(messages, ctx)).thenThrow(new AssistantUnavailableException("groq fail"));
        when(gemini.isUsable()).thenReturn(true);
        when(gemini.complete(messages, ctx)).thenReturn("legacy-gemini");

        String out = port.complete(messages, ctx);

        assertThat(out).isEqualTo("legacy-gemini");
    }

    @Test
    @DisplayName("legacy: groq düşer & gemini.isUsable=false → groq hatası yeniden fırlatılır")
    void legacyGroqFailsGeminiNotUsableRethrows() {
        when(props.getChain()).thenReturn(null);
        when(props.isUsable()).thenReturn(true);
        when(groq.complete(messages, ctx)).thenThrow(new AssistantUnavailableException("only-groq-fail"));
        when(gemini.isUsable()).thenReturn(false);

        assertThatThrownBy(() -> port.complete(messages, ctx))
                .isInstanceOf(AssistantUnavailableException.class)
                .hasMessage("only-groq-fail");

        verify(gemini, never()).complete(any(), any());
    }

    @Test
    @DisplayName("legacy: props.isUsable=false & gemini.isUsable=true → doğrudan gemini.complete")
    void legacyGroqNotUsableGeminiOk() {
        when(props.getChain()).thenReturn(null);
        when(props.isUsable()).thenReturn(false);
        when(gemini.isUsable()).thenReturn(true);
        when(gemini.complete(messages, ctx)).thenReturn("direct-gemini");

        String out = port.complete(messages, ctx);

        assertThat(out).isEqualTo("direct-gemini");
        verify(groq, never()).complete(any(), any());
    }

    @Test
    @DisplayName("legacy: props.isUsable=false & gemini.isUsable=false → 'No assistant provider configured'")
    void legacyNoneConfigured() {
        when(props.getChain()).thenReturn(null);
        when(props.isUsable()).thenReturn(false);
        when(gemini.isUsable()).thenReturn(false);

        assertThatThrownBy(() -> port.complete(messages, ctx))
                .isInstanceOf(AssistantUnavailableException.class)
                .hasMessage("No assistant provider configured");

        verifyNoInteractions(groq);
    }
}
