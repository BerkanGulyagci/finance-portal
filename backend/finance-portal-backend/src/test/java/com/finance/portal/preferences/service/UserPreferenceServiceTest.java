package com.finance.portal.preferences.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.preferences.domain.UserMarginAlertSetting;
import com.finance.portal.preferences.domain.UserPreference;
import com.finance.portal.preferences.repository.UserMarginAlertSettingRepository;
import com.finance.portal.preferences.repository.UserPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Saf JUnit 5 + Mockito (Spring context yok). {@link UserPreferenceService} davranışı:
 * normal tercih upsert/get, margin_alert_threshold_pct adanmış tabloya yönlendirme + 0-100 clamp,
 * getAll harmanlama ve kenar durumlar.
 */
@ExtendWith(MockitoExtension.class)
class UserPreferenceServiceTest {

    private static final String USER = "user-123";
    private static final String MARGIN_KEY = UserPreferenceService.KEY_MARGIN_ALERT_THRESHOLD_PCT;

    // Gerçek ObjectMapper: JsonNode değerleri tam tipiyle üretilsin.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UserPreferenceRepository repository;

    @Mock
    private UserMarginAlertSettingRepository marginAlertRepository;

    private UserPreferenceService service;

    @Captor
    private ArgumentCaptor<UserPreference> prefCaptor;

    @Captor
    private ArgumentCaptor<UserMarginAlertSetting> marginCaptor;

    @BeforeEach
    void setUp() {
        // ObjectMapper gerçek instance olduğundan @InjectMocks yerine manuel kurulum.
        service = new UserPreferenceService(repository, marginAlertRepository, objectMapper);
    }

    private JsonNode json(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // --- upsert: normal tercih ---

    @Test
    @DisplayName("upsert: yeni tercih UserPreference oluşturup JSON metni olarak kaydeder")
    void upsertCreatesNewPreference() {
        when(repository.findByUserIdAndPrefKey(USER, "theme")).thenReturn(Optional.empty());

        service.upsert(USER, "theme", json("\"dark\""));

        verify(repository).save(prefCaptor.capture());
        UserPreference saved = prefCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER);
        assertThat(saved.getPrefKey()).isEqualTo("theme");
        assertThat(saved.getValue()).isEqualTo("\"dark\"");
        verifyNoInteractions(marginAlertRepository);
    }

    @Test
    @DisplayName("upsert: mevcut tercihi günceller, yeni satır oluşturmaz")
    void upsertUpdatesExistingPreference() {
        UserPreference existing = new UserPreference();
        existing.setUserId(USER);
        existing.setPrefKey("lang");
        existing.setValue("\"tr\"");
        when(repository.findByUserIdAndPrefKey(USER, "lang")).thenReturn(Optional.of(existing));

        service.upsert(USER, "lang", json("\"en\""));

        verify(repository).save(prefCaptor.capture());
        assertThat(prefCaptor.getValue()).isSameAs(existing);
        assertThat(prefCaptor.getValue().getValue()).isEqualTo("\"en\"");
    }

    @Test
    @DisplayName("upsert: nesne değeri toString() ile JSON metni olarak saklanır")
    void upsertStoresObjectValueAsJsonText() {
        when(repository.findByUserIdAndPrefKey(USER, "layout")).thenReturn(Optional.empty());

        service.upsert(USER, "layout", json("{\"x\":1,\"y\":2}"));

        verify(repository).save(prefCaptor.capture());
        assertThat(prefCaptor.getValue().getValue()).isEqualTo("{\"x\":1,\"y\":2}");
    }

    @Test
    @DisplayName("upsert: null value -> kayıt değeri null")
    void upsertNullJsonValueStoresNull() {
        when(repository.findByUserIdAndPrefKey(USER, "ticker")).thenReturn(Optional.empty());

        service.upsert(USER, "ticker", null);

        verify(repository).save(prefCaptor.capture());
        assertThat(prefCaptor.getValue().getValue()).isNull();
    }

    @Test
    @DisplayName("upsert: JSON null node -> kayıt değeri null")
    void upsertJsonNullNodeStoresNull() {
        when(repository.findByUserIdAndPrefKey(USER, "ticker")).thenReturn(Optional.empty());

        service.upsert(USER, "ticker", objectMapper.nullNode());

        verify(repository).save(prefCaptor.capture());
        assertThat(prefCaptor.getValue().getValue()).isNull();
    }

    // --- upsert: geçersiz anahtar ---

    @Test
    @DisplayName("upsert: null anahtar -> hiçbir kayıt yapılmaz")
    void upsertNullKeyNoOp() {
        service.upsert(USER, null, json("\"x\""));
        verifyNoInteractions(repository);
        verifyNoInteractions(marginAlertRepository);
    }

    @Test
    @DisplayName("upsert: boş/whitespace anahtar -> hiçbir kayıt yapılmaz")
    void upsertBlankKeyNoOp() {
        service.upsert(USER, "   ", json("\"x\""));
        verifyNoInteractions(repository);
        verifyNoInteractions(marginAlertRepository);
    }

    // --- upsert: margin_alert_threshold_pct yönlendirmesi + clamp ---

    @Test
    @DisplayName("upsert margin: adanmış tabloya yazılır, UserPreference'a yazılmaz")
    void upsertMarginRoutesToMarginTable() {
        when(marginAlertRepository.findByUserId(USER)).thenReturn(Optional.empty());

        service.upsert(USER, MARGIN_KEY, json("70"));

        verify(marginAlertRepository).save(marginCaptor.capture());
        assertThat(marginCaptor.getValue().getMarginAlertThresholdPct()).isEqualTo(70);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("upsert margin: 100 üstü -> 100'e kıstırılır")
    void upsertMarginClampsAbove100() {
        when(marginAlertRepository.findByUserId(USER)).thenReturn(Optional.empty());

        service.upsert(USER, MARGIN_KEY, json("150"));

        verify(marginAlertRepository).save(marginCaptor.capture());
        assertThat(marginCaptor.getValue().getMarginAlertThresholdPct()).isEqualTo(100);
    }

    @Test
    @DisplayName("upsert margin: 0 altı -> 0'a kıstırılır")
    void upsertMarginClampsBelow0() {
        when(marginAlertRepository.findByUserId(USER)).thenReturn(Optional.empty());

        service.upsert(USER, MARGIN_KEY, json("-25"));

        verify(marginAlertRepository).save(marginCaptor.capture());
        assertThat(marginCaptor.getValue().getMarginAlertThresholdPct()).isEqualTo(0);
    }

    @Test
    @DisplayName("upsert margin: mevcut kayıt güncellenir, yeni instance oluşmaz")
    void upsertMarginUpdatesExistingRow() {
        UserMarginAlertSetting existing = new UserMarginAlertSetting(USER, 50);
        when(marginAlertRepository.findByUserId(USER)).thenReturn(Optional.of(existing));

        service.upsert(USER, MARGIN_KEY, json("33"));

        verify(marginAlertRepository).save(marginCaptor.capture());
        assertThat(marginCaptor.getValue()).isSameAs(existing);
        assertThat(marginCaptor.getValue().getMarginAlertThresholdPct()).isEqualTo(33);
    }

    @Test
    @DisplayName("upsert margin: null value -> varsayılan 50")
    void upsertMarginNullValueDefaults50() {
        when(marginAlertRepository.findByUserId(USER)).thenReturn(Optional.empty());

        service.upsert(USER, MARGIN_KEY, null);

        verify(marginAlertRepository).save(marginCaptor.capture());
        assertThat(marginCaptor.getValue().getMarginAlertThresholdPct()).isEqualTo(50);
    }

    @Test
    @DisplayName("upsert margin: JSON null node -> varsayılan 50")
    void upsertMarginJsonNullNodeDefaults50() {
        when(marginAlertRepository.findByUserId(USER)).thenReturn(Optional.empty());

        service.upsert(USER, MARGIN_KEY, objectMapper.nullNode());

        verify(marginAlertRepository).save(marginCaptor.capture());
        assertThat(marginCaptor.getValue().getMarginAlertThresholdPct()).isEqualTo(50);
    }

    @Test
    @DisplayName("upsert margin: parse edilemeyen metin -> varsayılan 50")
    void upsertMarginUnparseableValueDefaults50() {
        when(marginAlertRepository.findByUserId(USER)).thenReturn(Optional.empty());

        service.upsert(USER, MARGIN_KEY, json("\"abc\""));

        verify(marginAlertRepository).save(marginCaptor.capture());
        assertThat(marginCaptor.getValue().getMarginAlertThresholdPct()).isEqualTo(50);
    }

    @Test
    @DisplayName("upsert margin: null/blank userId -> hiçbir yazma yapılmaz")
    void upsertMarginBlankUserIdNoOp() {
        service.upsert("  ", MARGIN_KEY, json("70"));
        verify(marginAlertRepository, never()).save(any());
    }

    // --- getAll: birleşik harita ---

    @Test
    @DisplayName("getAll: kayıtlı tercihler + margin eşiği harmanlanır")
    void getAllMergesPreferencesAndMargin() {
        UserPreference theme = pref("theme", "\"dark\"");
        UserPreference layout = pref("layout", "{\"x\":1}");
        when(repository.findByUserId(USER)).thenReturn(List.of(theme, layout));
        when(marginAlertRepository.findByUserId(USER))
                .thenReturn(Optional.of(new UserMarginAlertSetting(USER, 80)));

        Map<String, JsonNode> result = service.getAll(USER);

        assertThat(result).containsKeys("theme", "layout", MARGIN_KEY);
        assertThat(result.get("theme").asText()).isEqualTo("dark");
        assertThat(result.get("layout").get("x").asInt()).isEqualTo(1);
        assertThat(result.get(MARGIN_KEY).asInt()).isEqualTo(80);
    }

    @Test
    @DisplayName("getAll: margin kaydı yoksa varsayılan 50 enjekte edilir")
    void getAllDefaultMarginWhenMissing() {
        when(repository.findByUserId(USER)).thenReturn(List.of());
        when(marginAlertRepository.findByUserId(USER)).thenReturn(Optional.empty());

        Map<String, JsonNode> result = service.getAll(USER);

        assertThat(result).hasSize(1);
        assertThat(result.get(MARGIN_KEY).asInt()).isEqualTo(50);
    }

    @Test
    @DisplayName("getAll: null/boş value'lu kayıtlar atlanır")
    void getAllSkipsBlankValues() {
        when(repository.findByUserId(USER)).thenReturn(List.of(pref("a", null), pref("b", "   ")));
        when(marginAlertRepository.findByUserId(USER)).thenReturn(Optional.empty());

        Map<String, JsonNode> result = service.getAll(USER);

        assertThat(result).doesNotContainKeys("a", "b");
        assertThat(result).containsKey(MARGIN_KEY);
    }

    @Test
    @DisplayName("getAll: bozuk JSON value sessizce atlanır, diğerleri kalır")
    void getAllSkipsCorruptJson() {
        when(repository.findByUserId(USER)).thenReturn(List.of(pref("good", "\"ok\""), pref("bad", "{not-json")));
        when(marginAlertRepository.findByUserId(USER)).thenReturn(Optional.empty());

        Map<String, JsonNode> result = service.getAll(USER);

        assertThat(result).containsKey("good");
        assertThat(result).doesNotContainKey("bad");
    }

    @Test
    @DisplayName("getAll: DB'deki margin eşiği de 0-100'e kıstırılır")
    void getAllClampsStoredMarginValue() {
        when(repository.findByUserId(USER)).thenReturn(List.of());
        when(marginAlertRepository.findByUserId(USER))
                .thenReturn(Optional.of(new UserMarginAlertSetting(USER, 250)));

        Map<String, JsonNode> result = service.getAll(USER);

        assertThat(result.get(MARGIN_KEY).asInt()).isEqualTo(100);
    }

    // --- getMarginAlertThresholdPct ---

    @Test
    @DisplayName("getMarginAlertThresholdPct: null userId -> 50 (DB'ye gitmez)")
    void getMarginNullUserDefaults50() {
        assertThat(service.getMarginAlertThresholdPct(null)).isEqualTo(50);
        verifyNoInteractions(marginAlertRepository);
    }

    @Test
    @DisplayName("getMarginAlertThresholdPct: kayıt yok -> 50")
    void getMarginNoRowDefaults50() {
        when(marginAlertRepository.findByUserId(USER)).thenReturn(Optional.empty());
        assertThat(service.getMarginAlertThresholdPct(USER)).isEqualTo(50);
    }

    @Test
    @DisplayName("getMarginAlertThresholdPct: kayıtlı değer döner")
    void getMarginReturnsStoredValue() {
        when(marginAlertRepository.findByUserId(USER))
                .thenReturn(Optional.of(new UserMarginAlertSetting(USER, 25)));
        assertThat(service.getMarginAlertThresholdPct(USER)).isEqualTo(25);
    }

    // --- delete ---

    @Test
    @DisplayName("delete: mevcut tercih silinir")
    void deleteDeletesWhenPresent() {
        UserPreference p = pref("theme", "\"dark\"");
        when(repository.findByUserIdAndPrefKey(USER, "theme")).thenReturn(Optional.of(p));

        service.delete(USER, "theme");

        verify(repository).delete(p);
    }

    @Test
    @DisplayName("delete: tercih yoksa silme çağrılmaz")
    void deleteNoOpWhenAbsent() {
        when(repository.findByUserIdAndPrefKey(USER, "theme")).thenReturn(Optional.empty());

        service.delete(USER, "theme");

        verify(repository, never()).delete(any());
    }

    private UserPreference pref(String key, String value) {
        UserPreference p = new UserPreference();
        p.setUserId(USER);
        p.setPrefKey(key);
        p.setValue(value);
        return p;
    }
}
