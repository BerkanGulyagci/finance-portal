package com.finance.portal.assistant.application.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.finance.portal.alarm.application.service.AlarmService;
import com.finance.portal.alarm.presentation.dto.CreateAlarmRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fiyat/değişim ALARMI kurar (giriş + ONAY gerekli). confirm=false iken kurmaz, sadece öneriyi döner;
 * LLM kullanıcıya onaylatır, kullanıcı onaylayınca confirm=true ile tekrar çağrılır. Alış/satış YAPMAZ.
 */
@Component
public class AlarmCreateTool implements AssistantTool {

    private final AlarmService alarmService;

    public AlarmCreateTool(AlarmService alarmService) {
        this.alarmService = alarmService;
    }

    @Override
    public String name() {
        return "create_alarm";
    }

    @Override
    public String description() {
        return "Kullanıcı için fiyat ALARMI kurar (giriş gerekli). asset_type + symbol (get_current_price kuralları), "
                + "metric (PRICE varsayılan / CHANGE_PERCENT / VOLUME), direction (ABOVE=üstüne çıkınca / BELOW=altına inince), "
                + "threshold (eşik değer). ÖNEMLİ: önce confirm OLMADAN çağır → öneriyi kullanıcıya göster ve onay iste; "
                + "SADECE kullanıcı açıkça onaylayınca confirm=true ile tekrar çağır. Alış/satış/işlem ASLA yapma.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "asset_type", Map.of("type", "string",
                                "enum", List.of("STOCK", "CRYPTO", "FX", "GOLD", "COMMODITY", "BOND", "FUND", "FUTURE")),
                        "symbol", Map.of("type", "string", "description", "Sembol (get_current_price kuralları)"),
                        "metric", Map.of("type", "string", "enum", List.of("PRICE", "CHANGE_PERCENT", "VOLUME")),
                        "direction", Map.of("type", "string", "enum", List.of("ABOVE", "BELOW")),
                        "threshold", Map.of("type", "number", "description", "Eşik değer"),
                        "confirm", Map.of("type", "boolean", "description", "Kullanıcı onayladıysa true")),
                "required", List.of("asset_type", "symbol", "direction", "threshold"));
    }

    @Override
    public String execute(JsonNode args, ToolContext ctx) {
        if (ctx == null || !ctx.isAuthenticated()) {
            return "Alarm kurmak için kullanıcının giriş yapması gerekir.";
        }
        String assetType = text(args, "asset_type").toUpperCase(Locale.ROOT);
        String symbol = text(args, "symbol").trim().toUpperCase(Locale.ROOT);
        String metric = text(args, "metric").isBlank() ? "PRICE" : text(args, "metric").toUpperCase(Locale.ROOT);
        String direction = text(args, "direction").toUpperCase(Locale.ROOT);
        JsonNode thr = args != null ? args.get("threshold") : null;
        if (symbol.isBlank() || assetType.isBlank() || direction.isBlank() || thr == null || !thr.isNumber()) {
            return "Eksik bilgi: varlık, sembol, yön (ABOVE/BELOW) ve eşik değer gerekli.";
        }
        BigDecimal threshold = thr.decimalValue();
        boolean confirm = boolArg(args, "confirm");

        String human = symbol + " " + metricTr(metric) + " "
                + (direction.equals("ABOVE") ? "≥ " : "≤ ") + threshold.toPlainString()
                + (metric.equals("CHANGE_PERCENT") ? "%" : (metric.equals("PRICE") ? " (fiyat)" : ""));

        if (!confirm) {
            return "ONAY_BEKLENIYOR: Şu alarmı kuracağım → " + human
                    + ". Kullanıcıya bunu net söyle ve 'Onaylıyor musun?' diye sor; onaylarsa confirm=true ile tekrar çağır.";
        }

        try {
            CreateAlarmRequest req = new CreateAlarmRequest();
            req.setAssetType(assetType);
            req.setSymbol(symbol);
            req.setMetric(metric);
            req.setDirection(direction);
            req.setThreshold(threshold);
            req.setFrequency("ONCE");
            req.setNote("Porti ile kuruldu");
            alarmService.create(ctx.userId(), ctx.userEmail(), req);
            return "Alarm kuruldu: " + human + ". Koşul gerçekleşince bildirim alacaksın.";
        } catch (Exception e) {
            return "Alarm kurulamadı: " + e.getMessage();
        }
    }

    private static String metricTr(String m) {
        return switch (m) {
            case "CHANGE_PERCENT" -> "günlük değişim";
            case "VOLUME" -> "hacim";
            default -> "fiyat";
        };
    }

    private static String text(JsonNode args, String field) {
        JsonNode n = args != null ? args.get(field) : null;
        return n != null && !n.isNull() ? n.asText("") : "";
    }

    private static boolean boolArg(JsonNode args, String field) {
        JsonNode n = args != null ? args.get(field) : null;
        return n != null && !n.isNull() && n.asBoolean(false);
    }
}
