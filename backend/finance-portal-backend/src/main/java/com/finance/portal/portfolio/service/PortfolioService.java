package com.finance.portal.portfolio.service;

import com.finance.portal.portfolio.dto.AddTransactionRequest;
import com.finance.portal.portfolio.dto.CreatePortfolioRequest;
import com.finance.portal.portfolio.dto.PortfolioResponse;

import java.util.List;
import java.util.UUID;

public interface PortfolioService {

    PortfolioResponse createPortfolio(String userId, CreatePortfolioRequest request);

    PortfolioResponse addTransaction(String userId, UUID portfolioId, AddTransactionRequest request);

    PortfolioResponse getPortfolioById(String userId, UUID portfolioId);

    List<PortfolioResponse> getUserPortfolios(String userId);
}
