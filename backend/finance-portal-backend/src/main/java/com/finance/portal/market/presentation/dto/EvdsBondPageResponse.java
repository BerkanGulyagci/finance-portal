package com.finance.portal.market.presentation.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * EVDS DİBS kıymet listesi sayfalı response DTO'su.
 */
@Getter
@NoArgsConstructor
public class EvdsBondPageResponse {

    private List<EvdsBondItemDto> items;
    private int page;
    private int size;
    private long totalItems;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    public static EvdsBondPageResponse of(
            List<EvdsBondItemDto> items,
            int page, int size, long totalItems) {
        EvdsBondPageResponse r = new EvdsBondPageResponse();
        r.items       = items;
        r.page        = page;
        r.size        = size;
        r.totalItems  = totalItems;
        r.totalPages  = size > 0 ? (int) Math.ceil((double) totalItems / size) : 0;
        r.hasNext     = (long) (page + 1) * size < totalItems;
        r.hasPrevious = page > 0;
        return r;
    }
}
