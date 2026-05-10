package com.finance.portal.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreatePortfolioRequest {

    @NotBlank(message = "name must not be blank")
    @Size(max = 100)
    private String name;

    private String description;

    @Size(min = 3, max = 3, message = "currency must be exactly 3 characters")
    private String currency;

    /**
     * Portföy tipi. Frontend göndermezse null olabilir.
     * Service katmanında null ise HOLDINGS default kullanılır.
     */
    private String portfolioType;
}
