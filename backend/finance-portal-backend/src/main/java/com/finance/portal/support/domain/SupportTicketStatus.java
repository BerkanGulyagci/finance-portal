package com.finance.portal.support.domain;

/**
 * Destek talebi durumu (3 aşamalı): Açık → İlgileniliyor → Çözüldü.
 */
public enum SupportTicketStatus {

    OPEN("Açık"),
    IN_PROGRESS("İlgileniliyor"),
    RESOLVED("Çözüldü");

    private final String label;

    SupportTicketStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static SupportTicketStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return SupportTicketStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
