package com.finance.portal.assistant.application;

import com.finance.portal.assistant.application.model.ChatMessage;
import com.finance.portal.assistant.application.port.AssistantChatPort;
import com.finance.portal.assistant.application.tools.ToolContext;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Warren AI sohbet orkestrasyonu: guardrail sistem promptu + geçmiş kırpma + limit + sağlayıcı çağrısı.
 * Yalnızca finans/ekonomi kapsamı; alakasız sorular kibarca reddedilir (prompt seviyesinde).
 */
@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    /** Porti sistem promptu (dengeli/esnek guardrail). Kısa tutulur (Groq ücretsiz TPM limiti). */
    private static String buildSystemPrompt(String userName) {
        String namePart = (userName != null && !userName.isBlank())
                ? "Kullanıcının adı: " + userName.trim()
                        + ". Selamlarken ve uygun yerlerde adıyla samimi hitap et (ör. \"Merhaba " + userName.trim() + "\")."
                : "Kullanıcı giriş yapmamış olabilir; adını bilmiyorsan genel hitap kullan.";
        return """
            Sen "Porti"sin — Portiva finans portalının yapay zekâ asistanısın. Bugün: %s.
            %s
            Uzmanlığın finans/ekonomi (hisse, döviz, altın, emtia, kripto, fon, tahvil/eurobond, faiz, enflasyon, \
            kişisel finans); bu konularda öğretici yardım et. Finans dışı genel sorulara da kibarca ve kısaca yardımcı ol. \
            Kullanıcının yazdığı DİLDE yanıtla (Türkçe yazana Türkçe, İngilizce yazana İngilizce); varsayılan Türkçe. \
            Basit/anlık sorulara KISA cevap ver; ama risk/analiz/yatırım/karşılaştırma sorularına DERİN, yapılandırılmış \
            (başlık + madde, ARTI-EKSİ, gerekçeli, uzman seviyesi) cevap ver — gereksiz kısaltma yapma. Net bir DURUŞ/eğilim \
            belirtmekten çekinme (ör. "bence kısa vade için bu mantıklı / mantıklı değil, çünkü…"); sürekli hedge'leme, ama \
            nihai kararı kullanıcıya bırak. Kesin al/sat tavsiyesi verme; bir yatırım aracını yorumlarsan sonuna \
            "Bu içerik yatırım tavsiyesi değildir." ekle.
            YAZMA İŞLEMLERİ: Sadece (a) fiyat ALARMI kurma (create_alarm) ve (b) izleme listesine/favorilere ekleme \
            (add_to_watchlist) yapabilirsin. ASLA alış/satış/işlem yapma veya para hareketi gerçekleştirme. Bu iki aracı \
            çağırmadan ÖNCE ne yapacağını net özetleyip "Onaylıyor musun?" diye SOR; SADECE kullanıcı açıkça onaylayınca \
            (evet/onayla) confirm=true ile çağır. "ONAY_BEKLENIYOR" gibi teknik etiketleri kullanıcıya gösterme.

            Canlı veya geçmiş fiyat sorulduğunda TAHMİN ETME; get_current_price / get_price_at_date araçlarını çağır. \
            Portföy / kâr-zarar / varlık dağılımı sorularında get_portfolio_summary (giriş gerekli; sonuç "giriş yapmamış" \
            derse kullanıcıdan giriş yapmasını iste). Portföy özetinde varlık BAZINDA dökümü OTOMATİK verme; özet \
            (toplam + dağılım) sonrası "Detaylı varlık dökümünü ister misin?" diye SOR. Kullanıcı isterse (evet/detay/döküm) \
            get_portfolio_summary'i detailed=true ile tekrar çağırıp dökümü ver. "[DÖKÜM_YOK...]" gibi teknik etiketleri \
            kullanıcıya GÖSTERME. Güncel haber sorularında get_news. Dönemsel performans veya teknik \
            (trend, RSI, MA, "X dönemde ne yaptı") sorularında get_price_history. \
            Türkiye makroekonomi sorularında (politika faizi, "ülke faizi", faiz, enflasyon/TÜFE/ÜFE/çekirdek, \
            işsizlik, büyüme/GSYİH, cari denge, bütçe, rezerv, kapasite, tüketici güveni, kur) get_economy_indicator \
            çağır ve gerçek değeri YORUMLA — "TCMB sitesine bak / güncel kaynaklara başvur" deyip GEÇME. \
            "Gelecekte ne olur / yükselir mi" gibi tahmin sorularında KESİN konuşma; get_price_history verisine \
            dayanarak DENGELİ olası senaryolar (hem yukarı hem aşağı) sun, belirsizliği vurgula, kesin al/sat deme. \
            Kullanıcı "tek kelime" / "kısaca" cevap istese bile düz "Belirsiz" deyip BIRAKMA; bunun yerine \
            "Her iki yöne de gidebilir (artabilir/azalabilir)" gibi dürüst ama yardımcı bir ifade kullan ve \
            kısaca yönü belirleyecek 1-2 etkeni (faiz, kur, talep, trend/RSI) ekle — net tahmin edilemeyeceğini söyle. \
            Araç bir değer döndürdüğünde o değeri AÇIK bir cümleyle yaz (ör. "Dolar şu an 45,71 TL."); yalnızca uyarı \
            cümlesiyle yanıtlama. get_news başlıklarını madde madde (•) kısa özetle AKTAR; "işte haberler" gibi boş \
            cümle kurma. Tarihleri bugüne göre YYYY-MM-DD'ye çevir. Hisseler yalnız BIST'tir (yabancı borsa yok).
            Sembol kuralları — FX: USD, EUR, GBP, CHF, JPY; CRYPTO: BTC, ETH, SOL; \
            GOLD: GRAM (gram), GOLD (ons), CEYREK, YARIM, TAM, CUMHUR; STOCK: THYAO.IS gibi .IS ekli, endeks XU100.IS/XU030.IS.

            DERİN FİNANS ANALİZİ — sadece veri okuyan değil, YORUMLAYAN bir finans danışmanısın (ama yatırım tavsiyesi vermezsin):
            • Bir enstrümanın RİSK durumu sorulursa (ör. "THYAO'nun riski ne", "bu hisse riskli mi"): get_price_history ile \
              MA20/MA50/RSI/dönem getirisini çek; bunlardan VOLATİLİTE (fiyat ne kadar oynak), TREND (fiyat MA20/MA50 üstünde mi \
              altında mı), MOMENTUM (RSI>70 aşırı alım / <30 aşırı satım) ve varsa 52-hafta konumunu yorumlayıp DENGELİ bir risk \
              seviyesi (Düşük / Orta / Yüksek) ver, gerekçesini 1-2 cümleyle açıkla. Genel kural: kripto ve VİOP doğası gereği \
              YÜKSEK risk; tahvil/mevduat DÜŞÜK; hisse ORTA-YÜKSEK; fon türüne göre değişir. Kesin "düşer/çıkar" DEME.
            • "Bu fon / bu VİOP / bu hisse MANTIKLI bir yatırım mı" sorularında: getirisini (get_price_history), güncel fiyatını, \
              riskini ve VİOP için KALDIRAÇ + TEMİNAT riskini DENGELİ tart; ARTILARINI ve EKSİLERİNİ ayrı ayrı söyle; kullanıcının \
              vade/risk profilini sor; nihai kararı KULLANICIYA bırak ("sana uygun olur mu" çerçevesi, "al" deme).
            • "X TL ile kısa/uzun vadede ne yapabilirim / en verimli senaryolar" sorularında: tek araç önermek yerine SEÇENEKLERİ \
              vade ve risk iştahına göre sun — düşük risk (mevduat / likit fon / altın), orta (çeşitlendirilmiş hisse-fon), yüksek \
              (tekil hisse / kripto / VİOP); her birinin getiri potansiyeli VE riskini güncel veriyle (get_current_price / \
              get_price_history) belirt; çeşitlendirme + acil-durum fonu + vade-uyumu ilkelerini vurgula; kesin "şunu al" DEME.
            • Finans kavramlarını (risk, volatilite, Sharpe, çeşitlendirme, likidite, vade, reel getiri, kaldıraç, teminat, \
              maliyet ortalaması, beta, drawdown) hem DOĞRU hem SADE açıkla; Türkiye bağlamını (enflasyon, kur, faiz) gözet.
            • ENSTRÜMAN SORGULAMA — "yapamam / araçlarımda yok" deyip BIRAKMA: Belirli bir tahvil/DİBS/Eurobond (ISIN/kod, \
              ör. TRT100626K35) için get_price_history'yi asset_type=BOND ile çağır; vadeyi KODDAN oku (TRTggaayy → gün-ay-yıl; \
              TRT100626... = 10 Haz 2026) ve vadeye kalan süreyi + risk profilini (vade kısaldıkça risk DÜŞER, getiri risksiz \
              faize yakınsar) yorumla. Fon için asset_type=FUND + TEFAS kodu (ör. AFA, TTE). Kod yanlış/bulunamazsa tam ad/kodu iste.
            • Cevabını mümkünse kullanıcının vade/risk profilini öğrenen kısa bir TAKİP SORUSUYLA bitir (daha isabetli yardım için).

            SİTE HAKİMİYETİ — Portiva'da neler yapıldığını bilir, "bu sitede ne yapabilirim" sorusunu özetlersin: çoklu PORTFÖY \
            oluşturma + işlem (alış/satış/kupon) ekleme + holdings takibi (anlık değer, K/Z, reel getiri, MA, trend); portföyü \
            "AI ile Analiz Et" (risk/sağlık skoru, Sharpe/volatilite/drawdown, benchmark kıyas, kriz stres testi, AI yorum); \
            "Ne Olurdu?" fırsat-maliyeti; İZLEME listesi (⭐); fiyat/değişim/hacim ALARMLARI (e-posta+bildirim); PİYASA sayfaları \
            (Hisse/Kripto/Döviz/Altın/Emtia/Tahvil-Eurobond/Fon/Ekonomi) detay grafik + indikatör + KIYASLAMA; kişiselleştirilmiş \
            HABER; özelleştirilebilir DASHBOARD; haftalık/aylık e-bülten; profil + destek talebi.

            "Nasıl yaparım" sorularında yönlendir: Portföy → üst menü "Portföyler" → "Yeni Portföy Oluştur", içeride "İşlem Ekle". \
            Portföyü DETAYLI analiz (risk/sağlık skoru, volatilite/Sharpe, benchmark kıyas, reel getiri, kriz stres testleri) \
            isteyen kullanıcıyı portföy detayındaki "AI ile Analiz Et" butonuna yönlendir (ör. "Portföyünü detaylı analiz \
            etmem için portföy detayında 'AI ile Analiz Et'e tıklayabilirsin"). \
            Favori → yıldız ⭐ ikonu. Piyasa sayfaları üst "Piyasa" menüsünde (Hisseler/Kripto/Döviz/Altın/Emtia/Tahviller[Eurobond]/Fonlar/Ekonomi). \
            Alarm → enstrüman detayında "Alarm" butonu. Haberler → üst menü "Haberler".
            Kısaltmalar: DİBS = Devlet İç Borçlanma Senedi, VİOP = Vadeli İşlem ve Opsiyon Piyasası, TEFAS = fon platformu. \
            CDS, MA, RSI, Bollinger gibi terimleri sade dille açıkla.
            """.formatted(LocalDate.now(), namePart);
    }

    private final AssistantChatPort chatPort;
    private final AssistantRateLimiter rateLimiter;
    private final AssistantProperties props;
    private final CentralIntegrationLogService integrationLog;
    private final AssistantToolUsageTracker usageTracker;
    private final StringRedisTemplate redis;

    public AssistantService(AssistantChatPort chatPort,
                            AssistantRateLimiter rateLimiter,
                            AssistantProperties props,
                            CentralIntegrationLogService integrationLog,
                            AssistantToolUsageTracker usageTracker,
                            StringRedisTemplate redis) {
        this.chatPort = chatPort;
        this.rateLimiter = rateLimiter;
        this.props = props;
        this.integrationLog = integrationLog;
        this.usageTracker = usageTracker;
        this.redis = redis;
    }

    public enum Status { OK, LOGIN_REQUIRED, RATE_LIMITED, UNAVAILABLE, BUSY, INVALID }

    public record AssistantReply(Status status, String reply) {
        public static AssistantReply ok(String reply)   { return new AssistantReply(Status.OK, reply); }
        public static AssistantReply of(Status status)  { return new AssistantReply(status, null); }
    }

    public AssistantReply chat(List<ChatMessage> incoming, String userId, String userName, String userEmail, String clientIp) {
        if (!props.isUsable() && !props.isGeminiUsable()) {
            return AssistantReply.of(Status.UNAVAILABLE);
        }
        if (incoming == null || incoming.isEmpty()) {
            return AssistantReply.of(Status.INVALID);
        }

        List<ChatMessage> sanitized = sanitizeHistory(incoming);
        if (sanitized.isEmpty()) {
            return AssistantReply.of(Status.INVALID);
        }

        // Cache (araçsız tek-soru yanıtları): isabet AI'ye VE limite gitmez — terim/nav tekrarları bedava.
        String cacheKey = singleTurnCacheKey(sanitized);
        if (cacheKey != null) {
            String hit = cacheGet(cacheKey);
            if (hit != null && !hit.isBlank()) {
                return AssistantReply.ok(hit);
            }
        }

        // Limit kontrolü (mesaj başına say). Aşılırsa sağlayıcı çağrılmaz.
        AssistantRateLimiter.Decision decision = rateLimiter.register(userId, clientIp);
        if (decision == AssistantRateLimiter.Decision.LOGIN_REQUIRED) {
            return AssistantReply.of(Status.LOGIN_REQUIRED);
        }
        if (decision == AssistantRateLimiter.Decision.RATE_LIMITED) {
            return AssistantReply.of(Status.RATE_LIMITED);
        }

        List<ChatMessage> payload = new ArrayList<>(sanitized.size() + 1);
        payload.add(ChatMessage.system(buildSystemPrompt(userName)));
        payload.addAll(sanitized);

        long start = System.currentTimeMillis();
        try {
            usageTracker.reset();
            String reply = chatPort.complete(payload, new ToolContext(userId, userName, userEmail));
            // Araç KULLANILMADIYSA (terim/nav vb. deterministik) cache'le; canlı veri cache'lenmez.
            if (cacheKey != null && usageTracker.count() == 0) {
                cachePut(cacheKey, reply);
            }
            return AssistantReply.ok(reply);
        } catch (AssistantChatPort.AssistantUnavailableException e) {
            integrationLog.publish(
                    IntegrationLogSupport.EVENT_EXTERNAL_API_FAILED,
                    "WARN",
                    "Warren AI sağlayıcı çağrısı başarısız: " + e.getMessage(),
                    IntegrationLogSupport.PROVIDER_GROQ,
                    "assistant_chat",
                    null,
                    System.currentTimeMillis() - start,
                    null,
                    null,
                    Map.of("provider", props.getProvider(), "model", props.getModel(),
                            "trigger", IntegrationLogSupport.TRIGGER_API_REQUEST),
                    AssistantService.class.getName());
            // 429 → sağlayıcı kotası/yoğunluğu (günlük ücretsiz token limiti dahil) → ayrı mesaj.
            boolean rateLimited = e.getMessage() != null && e.getMessage().contains("429");
            return AssistantReply.of(rateLimited ? Status.BUSY : Status.UNAVAILABLE);
        } finally {
            // Tomcat thread havuza geri dönmeden ThreadLocal'ı temizle (Sonar S5164).
            usageTracker.clear();
        }
    }

    /** Tek-soru (tek user mesajı) ise cache anahtarı; değilse/cache kapalıysa null. */
    private String singleTurnCacheKey(List<ChatMessage> sanitized) {
        if (props.getCacheTtlSeconds() <= 0 || sanitized.size() != 1) {
            return null;
        }
        ChatMessage m = sanitized.get(0);
        if (!"user".equals(m.role()) || m.content() == null) {
            return null;
        }
        String norm = m.content().trim().toLowerCase(Locale.forLanguageTag("tr"))
                .replaceAll("\\s+", " ").replaceAll("[?!.…]+$", "").trim();
        if (norm.isBlank()) {
            return null;
        }
        return "assistant:qa:" + sha256(norm);
    }

    private String cacheGet(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (Exception e) {
            return null; // Redis yoksa cache'siz devam
        }
    }

    private void cachePut(String key, String value) {
        try {
            redis.opsForValue().set(key, value, Duration.ofSeconds(props.getCacheTtlSeconds()));
        } catch (Exception ignored) {
            // Redis yoksa yok say
        }
    }

    private static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    /** Son N mesajı al, role'leri user/assistant'a sınırla, içerikleri kırp, boşları at. */
    private List<ChatMessage> sanitizeHistory(List<ChatMessage> incoming) {
        int from = Math.max(0, incoming.size() - props.getHistoryLimit());
        List<ChatMessage> out = new ArrayList<>();
        for (int i = from; i < incoming.size(); i++) {
            ChatMessage m = incoming.get(i);
            if (m == null || m.content() == null || m.content().isBlank()) {
                continue;
            }
            String role = "assistant".equalsIgnoreCase(m.role()) ? "assistant" : "user";
            String content = m.content().trim();
            if (content.length() > props.getMaxInputChars()) {
                content = content.substring(0, props.getMaxInputChars());
            }
            out.add(new ChatMessage(role, content));
        }
        // Son mesaj kullanıcıdan değilse (tutarsız istek) yine de gönderilebilir; ama hiç user yoksa geçersiz.
        boolean hasUser = out.stream().anyMatch(m -> "user".equals(m.role()));
        return hasUser ? out : List.of();
    }
}
