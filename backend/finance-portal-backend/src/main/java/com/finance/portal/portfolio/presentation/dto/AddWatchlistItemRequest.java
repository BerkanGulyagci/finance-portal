package com.finance.portal.portfolio.presentation.dto;

import com.finance.portal.common.domain.AssetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AddWatchlistItemRequest {

    @NotBlank(message = "symbol must not be blank")
    private String symbol;

    @NotNull(message = "assetType must not be null")
    private AssetType assetType;

    /** İsteğe bağlı not. */
    private String notes;
}
