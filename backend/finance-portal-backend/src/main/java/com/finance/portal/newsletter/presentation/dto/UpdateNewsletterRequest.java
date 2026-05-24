package com.finance.portal.newsletter.presentation.dto;

import com.finance.portal.newsletter.domain.NewsletterFrequency;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateNewsletterRequest {

    @NotNull
    private NewsletterFrequency frequency;

    private boolean enabled = true;
}
