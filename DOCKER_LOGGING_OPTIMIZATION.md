# Docker Logging Optimizasyonu

## Yapılan Değişiklikler

### 1. Docker Container Log Limitleri (docker-compose.yml)

Tüm servislere aşağıdaki logging konfigürasyonu eklendi:

```yaml
logging:
  driver: "json-file"
  options:
    max-size: "10m"
    max-file: "3"
```

**Etkilenen Servisler:**
- ✅ backend
- ✅ frontend
- ✅ log-consumer
- ✅ kafka
- ✅ opensearch
- ✅ opensearch-dashboards
- ✅ postgres
- ✅ redis
- ✅ keycloak
- ✅ apacheds
- ✅ otel-collector

**Sonuç:** Her container maksimum 3 adet 10MB'lık log dosyası tutacak = **maksimum 30MB/container**

### 2. Backend Log Seviyesi Optimizasyonu (log4j2-spring.xml)

**Değişiklik:**
```xml
<!-- ÖNCE: DEBUG seviyesi -->
<Logger name="com.finance.portal.common.infrastructure.logging" level="DEBUG" additivity="false">

<!-- SONRA: INFO seviyesi -->
<Logger name="com.finance.portal.common.infrastructure.logging" level="INFO" additivity="false">
```

**Etki:** Kafka publisher'ın her mesaj için debug logu yerine sadece önemli mesajlar loglanacak.

### 3. Log Consumer Optimizasyonu (LogConsumerService.java)

**Değişiklik:**
```java
// ÖNCE: Her mesaj için INFO log
log.info("Received Kafka log message. length={}, payload={}", message.length(), message);
log.info("Indexed to OpenSearch: index={} id={} result={}", indexName, response.id(), response.result());

// SONRA: DEBUG seviyesine çekildi
log.debug("Received Kafka log message. length={}", message.length());
log.debug("Indexed to OpenSearch: index={} id={} result={}", indexName, response.id(), response.result());
```

**Etki:** Log consumer'ın her mesaj için verbose log basması engellendi. Sadece hatalar INFO seviyesinde loglanacak.

### 4. OpenTelemetry Collector (Öneri)

**Mevcut Durum:**
```yaml
exporters:
  debug:
    verbosity: detailed  # Çok fazla log üretir
```

**Gelecek İyileştirme Önerisi:**
Trace doğrulama tamamlandıktan sonra:
```yaml
exporters:
  debug:
    verbosity: basic  # veya normal
```

**Not:** Şu an trace doğrulama için `detailed` seviyesi korundu. İleride production'a geçerken `basic` veya `normal`'e çekilebilir.

## Test Komutları

### 1. Servisleri Yeniden Başlat
```bash
cd 32bit-finance-portal-backend
docker compose down
docker compose up -d
```

### 2. Container Durumlarını Kontrol Et
```bash
docker compose ps
```

### 3. Log Çıktılarını İncele
```bash
# Backend logları
docker logs finance-portal-backend --tail 30

# Log consumer logları
docker logs finance-portal-log-consumer --tail 30

# OpenTelemetry Collector logları
docker logs finance-portal-otel-collector --tail 30

# Kafka logları
docker logs kafka --tail 30

# OpenSearch logları
docker logs finance-portal-opensearch --tail 30
```

### 4. Log Dosya Boyutlarını Kontrol Et
```bash
# Windows PowerShell
docker inspect finance-portal-backend | Select-String -Pattern "LogPath"

# Veya tüm container'ların log path'lerini görmek için
docker inspect $(docker ps -q) | Select-String -Pattern "LogPath"
```

### 5. Logging Konfigürasyonunu Doğrula
```bash
docker inspect finance-portal-backend | Select-String -Pattern "max-size|max-file"
```

## Beklenen Sonuçlar

### Log Seviyesi Değişiklikleri
- ✅ Backend: Kafka publisher debug logları azaldı
- ✅ Log Consumer: Her mesaj için INFO log yerine sadece hata durumlarında log
- ✅ OpenTelemetry: Şu an detailed (ileride basic'e çekilebilir)

### Disk Kullanımı
- **Önce:** Container logları sınırsız büyüyebiliyordu
- **Sonra:** Her container maksimum 30MB (3 x 10MB) log tutacak
- **11 servis × 30MB = ~330MB maksimum log disk kullanımı**

### Pipeline Durumu
- ✅ Kafka → log-consumer → OpenSearch pipeline çalışmaya devam ediyor
- ✅ OpenTelemetry trace collection çalışıyor
- ✅ Uygulama logları hala Kafka'ya gönderiliyor
- ✅ OpenSearch'te loglar indexleniyor

## Doğrulama Checklist

- [ ] Tüm servisler başarıyla başladı (`docker compose ps`)
- [ ] Backend logları INFO seviyesinde (`docker logs finance-portal-backend`)
- [ ] Log consumer sadece startup ve hata logları basıyor
- [ ] Kafka mesajları OpenSearch'e ulaşıyor (OpenSearch Dashboards'dan kontrol)
- [ ] OpenTelemetry trace'ler collector'a ulaşıyor
- [ ] Container log dosyaları 10MB'ı geçmiyor

## Ek Notlar

### Neden 10MB × 3 dosya?
- **10MB:** Orta büyüklükte bir log dosyası, debug için yeterli
- **3 dosya:** Rotation ile son 30MB log saklanır, eski loglar otomatik silinir
- **Toplam 30MB/container:** Makul disk kullanımı, debug için yeterli geçmiş

### Log Rotation Nasıl Çalışır?
1. Container log dosyası 10MB'a ulaşınca Docker otomatik rotate eder
2. Eski dosya `.1` uzantısı alır
3. 3 dosya dolunca en eski (`.3`) silinir
4. Yeni log dosyası oluşturulur

### Production İçin Öneriler
- OpenTelemetry Collector `verbosity: basic` veya `normal`'e çekilebilir
- Kritik servisler için `max-file: "5"` artırılabilir
- Log aggregation sistemi (ELK, Loki) kullanılıyorsa `max-file: "2"` yeterli

## Sorun Giderme

### Log dosyaları hala büyüyorsa
```bash
# Mevcut log dosyalarını temizle
docker compose down
docker system prune -a --volumes
docker compose up -d
```

### Logging config uygulanmadıysa
```bash
# Container'ı yeniden oluştur
docker compose up -d --force-recreate <service-name>
```

### Log seviyesi değişmedi
Backend ve log-consumer için image'ları yeniden build et:
```bash
docker compose build backend log-consumer
docker compose up -d backend log-consumer
```
