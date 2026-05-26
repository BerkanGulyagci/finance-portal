package com.finance.portal.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.portal.alarm.application.service.AlarmService;
import com.finance.portal.alarm.presentation.dto.CreateAlarmRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AlarmCreateToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private AlarmService alarmService;
    private AlarmCreateTool tool;
    private final ToolContext authedCtx = new ToolContext("u1", "Berkan", "berkan@example.com");

    @BeforeEach
    void setUp() {
        alarmService = mock(AlarmService.class);
        tool = new AlarmCreateTool(alarmService);
    }

    // ------------------------------ metadata / şema ------------------------------

    @Test
    @DisplayName("name() / description() — tool-calling şema sabitleri")
    void schemaConstants() {
        assertThat(tool.name()).isEqualTo("create_alarm");
        assertThat(tool.description())
                .contains("ALARMI")
                .contains("confirm")
                .contains("Alış/satış")
                .doesNotContain("buy");  // alış/satış asla
    }

    @Test
    @DisplayName("parameters() — required listede asset_type, symbol, direction, threshold")
    @SuppressWarnings("unchecked")
    void parameters_listsRequired() {
        java.util.Map<String,Object> schema = tool.parameters();

        assertThat(schema).containsEntry("type", "object");
        assertThat((java.util.List<String>) schema.get("required"))
                .containsExactlyInAnyOrder("asset_type", "symbol", "direction", "threshold");
    }

    // ------------------------------ auth gate ------------------------------

    @Test
    @DisplayName("execute: anon ctx → giriş yapması gerek mesajı, AlarmService çağrılmaz")
    void execute_anonContext_blocked() {
        ToolContext anon = new ToolContext(null, null, null);

        String r = tool.execute(node("{\"asset_type\":\"STOCK\",\"symbol\":\"THYAO\",\"direction\":\"ABOVE\",\"threshold\":300}"), anon);

        assertThat(r).contains("giriş yapması");
        verifyNoInteractions(alarmService);
    }

    @Test
    @DisplayName("execute: null ctx → giriş mesajı, AlarmService çağrılmaz")
    void execute_nullContext_blocked() {
        String r = tool.execute(node("{\"asset_type\":\"STOCK\",\"symbol\":\"THYAO\",\"direction\":\"ABOVE\",\"threshold\":300}"), null);

        assertThat(r).contains("giriş yapması");
        verifyNoInteractions(alarmService);
    }

    // ------------------------------ validation ------------------------------

    @Test
    @DisplayName("execute: eksik symbol → 'Eksik bilgi' uyarısı")
    void execute_missingSymbol_complains() {
        String r = tool.execute(node("{\"asset_type\":\"STOCK\",\"direction\":\"ABOVE\",\"threshold\":300}"), authedCtx);

        assertThat(r).contains("Eksik bilgi");
        verifyNoInteractions(alarmService);
    }

    @Test
    @DisplayName("execute: threshold sayı değil → 'Eksik bilgi'")
    void execute_thresholdNotNumber_complains() {
        String r = tool.execute(node("{\"asset_type\":\"STOCK\",\"symbol\":\"THYAO\",\"direction\":\"ABOVE\",\"threshold\":\"abc\"}"), authedCtx);

        assertThat(r).contains("Eksik bilgi");
        verifyNoInteractions(alarmService);
    }

    @Test
    @DisplayName("execute: eksik direction → 'Eksik bilgi'")
    void execute_missingDirection_complains() {
        String r = tool.execute(node("{\"asset_type\":\"STOCK\",\"symbol\":\"THYAO\",\"threshold\":300}"), authedCtx);

        assertThat(r).contains("Eksik bilgi");
        verifyNoInteractions(alarmService);
    }

    // ------------------------------ onay akışı (KRİTİK) ------------------------------

    @Test
    @DisplayName("execute: confirm=false → ONAY_BEKLENIYOR, AlarmService çağrılmaz")
    void execute_unconfirmed_returnsPendingMarker_doesNotCreate() {
        String r = tool.execute(node("{\"asset_type\":\"STOCK\",\"symbol\":\"thyao\",\"direction\":\"ABOVE\",\"threshold\":300}"), authedCtx);

        assertThat(r)
                .startsWith("ONAY_BEKLENIYOR")
                .contains("THYAO")        // sembol uppercase
                .contains("≥")            // ABOVE → ≥
                .contains("300")
                .contains("Onaylıyor musun");
        verify(alarmService, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("execute: confirm yokken bile alarm KURULMAZ (boş confirm = false)")
    void execute_confirmAbsent_treatedAsFalse() {
        // confirm alanı yok → boolArg false döner → onay bekleniyor
        String r = tool.execute(node("{\"asset_type\":\"CRYPTO\",\"symbol\":\"BTC\",\"direction\":\"BELOW\",\"threshold\":50000}"), authedCtx);

        assertThat(r).startsWith("ONAY_BEKLENIYOR");
        verifyNoInteractions(alarmService);
    }

    // ------------------------------ onaylı çağrı → AlarmService.create ------------------------------

    @Test
    @DisplayName("execute: confirm=true → AlarmService.create çağrılır; doğru request alanları")
    void execute_confirmed_callsAlarmService() {
        ArgumentCaptor<CreateAlarmRequest> captor = ArgumentCaptor.forClass(CreateAlarmRequest.class);

        String r = tool.execute(node(
                "{\"asset_type\":\"stock\",\"symbol\":\"thyao\",\"metric\":\"PRICE\",\"direction\":\"above\",\"threshold\":305.5,\"confirm\":true}"),
                authedCtx);

        verify(alarmService).create(eq("u1"), eq("berkan@example.com"), captor.capture());
        CreateAlarmRequest req = captor.getValue();
        assertThat(req.getAssetType()).isEqualTo("STOCK");        // uppercase normalize
        assertThat(req.getSymbol()).isEqualTo("THYAO");
        assertThat(req.getMetric()).isEqualTo("PRICE");
        assertThat(req.getDirection()).isEqualTo("ABOVE");
        assertThat(req.getThreshold()).isEqualByComparingTo("305.5");
        assertThat(req.getFrequency()).isEqualTo("ONCE");
        assertThat(req.getNote()).contains("Porti");
        assertThat(r).startsWith("Alarm kuruldu");
    }

    @Test
    @DisplayName("execute: metric belirtilmezse PRICE varsayılır")
    void execute_noMetric_defaultsToPrice() {
        String r = tool.execute(node(
                "{\"asset_type\":\"FX\",\"symbol\":\"USD\",\"direction\":\"ABOVE\",\"threshold\":45,\"confirm\":true}"),
                authedCtx);

        ArgumentCaptor<CreateAlarmRequest> captor = ArgumentCaptor.forClass(CreateAlarmRequest.class);
        verify(alarmService).create(any(), any(), captor.capture());
        assertThat(captor.getValue().getMetric()).isEqualTo("PRICE");
        assertThat(r).startsWith("Alarm kuruldu");
    }

    @Test
    @DisplayName("execute: CHANGE_PERCENT metric — Türkçe etiket 'günlük değişim' + %% görünür")
    void execute_changePercent_humanReadable() {
        String r = tool.execute(node(
                "{\"asset_type\":\"STOCK\",\"symbol\":\"THYAO\",\"metric\":\"CHANGE_PERCENT\",\"direction\":\"ABOVE\",\"threshold\":5,\"confirm\":false}"),
                authedCtx);

        assertThat(r).contains("günlük değişim").contains("%");
    }

    @Test
    @DisplayName("execute: BELOW yön → '≤' işareti görünür")
    void execute_belowDirection_usesLessOrEqual() {
        String r = tool.execute(node(
                "{\"asset_type\":\"CRYPTO\",\"symbol\":\"BTC\",\"direction\":\"BELOW\",\"threshold\":50000,\"confirm\":false}"),
                authedCtx);

        assertThat(r).contains("≤").doesNotContain("≥");
    }

    @Test
    @DisplayName("execute: AlarmService exception fırlatırsa → kullanıcıya 'Alarm kurulamadı' mesajı")
    void execute_alarmServiceThrows_returnsErrorMessage() {
        doThrow(new IllegalArgumentException("invalid threshold"))
                .when(alarmService).create(any(), any(), any());

        String r = tool.execute(node(
                "{\"asset_type\":\"STOCK\",\"symbol\":\"THYAO\",\"direction\":\"ABOVE\",\"threshold\":300,\"confirm\":true}"),
                authedCtx);

        assertThat(r).startsWith("Alarm kurulamadı").contains("invalid threshold");
    }

    // ------------------------------ helper ------------------------------

    private static JsonNode node(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
