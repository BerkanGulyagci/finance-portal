package com.finance.portal.market.presentation.dto;

import java.util.List;

/**
 * EVDS DİBS kıymet listesi sayfalı response DTO'su.
 */
public class EvdsBondPageResponse {

    private List<EvdsBondItemDto> items;
    private int page;
    private int size;
    private long totalItems;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    public EvdsBondPageResponse() {}

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

    public List<EvdsBondItemDto> getItems()    { return items; }
    public int getPage()                       { return page; }
    public int getSize()                       { return size; }
    public long getTotalItems()                { return totalItems; }
    public int getTotalPages()                 { return totalPages; }
    public boolean isHasNext()                 { return hasNext; }
    public boolean isHasPrevious()             { return hasPrevious; }
}
