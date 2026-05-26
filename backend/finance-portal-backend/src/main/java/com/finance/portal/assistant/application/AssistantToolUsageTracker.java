package com.finance.portal.assistant.application;

import org.springframework.stereotype.Component;

/**
 * İstek başına (thread) araç çağrısı sayacı. Cache kararı için kullanılır:
 * araç KULLANILMAYAN yanıtlar (terim/navigasyon açıklamaları) deterministiktir → cache'lenebilir;
 * araç kullanan yanıtlar (canlı fiyat/haber/portföy) cache'lenmez.
 */
@Component
public class AssistantToolUsageTracker {

    private final ThreadLocal<int[]> count = ThreadLocal.withInitial(() -> new int[1]);

    public void reset() {
        count.get()[0] = 0;
    }

    public void increment() {
        count.get()[0]++;
    }

    public int count() {
        return count.get()[0];
    }

    /**
     * İstek bittiğinde Tomcat thread'i havuza geri döner; ThreadLocal'ı silmezsek
     * sonraki istek bu thread'i kullandığında eski değeri görür ve uzun vadede
     * ClassLoader/heap sızıntısı oluşur. AssistantService.chat() sonunda finally
     * bloğunda çağırılır.
     */
    public void clear() {
        count.remove();
    }
}
