package com.finance.portal.market.infrastructure.external.fx;

import com.finance.portal.common.application.exception.ExternalApiException;
import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
import com.finance.portal.market.infrastructure.external.fx.dto.TcmbCurrencyDto;
import com.finance.portal.market.infrastructure.external.fx.dto.TcmbExchangeRatesDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class TcmbFxClient {

    private static final Logger log = LoggerFactory.getLogger(TcmbFxClient.class);

    private final RestTemplate restTemplate;
    private final CentralIntegrationLogService integrationLog;

    @Value("${market.fx.tcmb.url}")
    private String tcmbUrl;

    public TcmbFxClient(RestTemplate restTemplate, CentralIntegrationLogService integrationLog) {
        this.restTemplate = restTemplate;
        this.integrationLog = integrationLog;
    }

    private void logIntegrationFailure(String eventType, String message, String httpStatus, Map<String, Object> metadata) {
        integrationLog.publish(
                eventType,
                "WARN",
                message,
                IntegrationLogSupport.PROVIDER_TCMB,
                "fetchLatestRates",
                httpStatus,
                null,
                null,
                Boolean.TRUE,
                metadata,
                TcmbFxClient.class.getName()
        );
    }

    public TcmbExchangeRatesDto fetchLatestRates() {
        log.debug("Fetching latest FX rates from TCMB: {}", tcmbUrl);

        try {
            String xmlResponse = restTemplate.getForObject(tcmbUrl, String.class);

            if (xmlResponse == null || xmlResponse.trim().isEmpty()) {
                logIntegrationFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_EMPTY_RESPONSE,
                        "TCMB FX: empty response body",
                        null,
                        Map.of("url", String.valueOf(tcmbUrl)));
                throw new ExternalApiException("TCMB API returned empty response");
            }

            TcmbExchangeRatesDto dto = parseTcmbXml(xmlResponse);

            // XML geldi ama hiç <Currency> elemanı parse edilemedi —
            // muhtemelen XML yapısı/eleman adları değişti (sessiz hata).
            if (dto.getCurrencies() == null || dto.getCurrencies().isEmpty()) {
                logIntegrationFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                        "TCMB FX: 0 Currency elements parsed from XML (XML structure may have changed)",
                        null,
                        Map.of("url", String.valueOf(tcmbUrl),
                                "currenciesNull", dto.getCurrencies() == null));
            }

            return dto;

        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == 429) {
                logIntegrationFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_RATE_LIMITED,
                        "TCMB FX: rate limited (HTTP 429)",
                        String.valueOf(ex.getStatusCode().value()),
                        Map.of("url", String.valueOf(tcmbUrl)));
            } else {
                logIntegrationFailure(
                        IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                        "TCMB FX client error: " + ex.getStatusCode(),
                        String.valueOf(ex.getStatusCode().value()),
                        Map.of("url", String.valueOf(tcmbUrl)));
            }
            throw new ExternalApiException(
                    "TCMB API returned a client error: " + ex.getStatusCode(), ex);
        } catch (HttpServerErrorException ex) {
            logIntegrationFailure(
                    IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                    "TCMB FX server error: " + ex.getStatusCode(),
                    String.valueOf(ex.getStatusCode().value()),
                    Map.of("url", String.valueOf(tcmbUrl)));
            throw new ExternalApiException(
                    "TCMB API is currently unavailable: " + ex.getStatusCode(), ex);
        } catch (ResourceAccessException ex) {
            throw new ExternalApiException(
                    "Failed to access TCMB API. Please check network connectivity.", ex);
        } catch (ExternalApiException ex) {
            throw ex;
        } catch (Exception ex) {
            logIntegrationFailure(
                    IntegrationLogSupport.EVENT_EXTERNAL_API_PARSE_FAILED,
                    "TCMB FX XML parse failed: " + ex.getMessage() + " (XML structure may have changed)",
                    null,
                    Map.of("url", String.valueOf(tcmbUrl),
                            "exceptionClass", ex.getClass().getSimpleName()));
            throw new ExternalApiException(
                    "Failed to parse TCMB XML response", ex);
        }
    }

    private TcmbExchangeRatesDto parseTcmbXml(String xmlResponse) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new InputSource(new StringReader(xmlResponse)));
        document.getDocumentElement().normalize();

        Element root = document.getDocumentElement();

        TcmbExchangeRatesDto dto = new TcmbExchangeRatesDto();
        dto.setDate(root.getAttribute("Date"));

        NodeList currencyNodes = root.getElementsByTagName("Currency");
        List<TcmbCurrencyDto> currencies = new ArrayList<>();

        for (int i = 0; i < currencyNodes.getLength(); i++) {
            Node node = currencyNodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element currencyElement = (Element) node;
            TcmbCurrencyDto currency = new TcmbCurrencyDto();

            String code = currencyElement.getAttribute("CurrencyCode");
            if (code == null || code.isBlank()) {
                code = currencyElement.getAttribute("Kod");
            }
            currency.setCurrencyCode(code);

            String unitText = getChildTextContent(currencyElement, "Unit");
            if (unitText != null && !unitText.isBlank()) {
                try {
                    currency.setUnit(Integer.parseInt(unitText.trim()));
                } catch (NumberFormatException ignored) {
                    // Leave unit as null; service layer will handle defaulting
                }
            }

            currency.setForexBuying(getChildTextContent(currencyElement, "ForexBuying"));
            currency.setForexSelling(getChildTextContent(currencyElement, "ForexSelling"));
            // Efektif (banknot) kurları — bazı dövizlerde boş gelebilir
            currency.setBanknoteBuying(getChildTextContent(currencyElement, "BanknoteBuying"));
            currency.setBanknoteSelling(getChildTextContent(currencyElement, "BanknoteSelling"));

            currencies.add(currency);
        }

        dto.setCurrencies(currencies);
        return dto;
    }

    private String getChildTextContent(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        Node node = nodes.item(0);
        String text = node.getTextContent();
        return text != null ? text.trim() : null;
    }
}
