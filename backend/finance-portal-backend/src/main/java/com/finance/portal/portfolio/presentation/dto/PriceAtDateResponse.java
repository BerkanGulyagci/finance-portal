package com.finance.portal.portfolio.presentation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Bir varlığın belirli bir tarihteki (veya o tarihten önceki en yakın işlem gününün)
 * kapanış fiyatı. İşlem ekleme modalında fiyatı otomatik doldurmak için kullanılır.
 *
 * @param price    bulunan fiyat (yoksa null)
 * @param date     fiyatın ait olduğu gerçek işlem günü (istenen tarih hafta sonu/tatil ise
 *                 ondan önceki en yakın gün)
 * @param found    fiyat bulundu mu
 */
public record PriceAtDateResponse(BigDecimal price, LocalDate date, boolean found) {
}
