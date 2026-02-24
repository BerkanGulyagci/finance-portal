package com.finance.portal.market.infrastructure.external.fx;

import com.finance.portal.common.infrastructure.exception.ExternalApiException;
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

@Component
public class TcmbFxClient {

    private static final Logger log = LoggerFactory.getLogger(TcmbFxClient.class);

    private final RestTemplate restTemplate;

    @Value("${market.fx.tcmb.url}")
    private String tcmbUrl;

    public TcmbFxClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public TcmbExchangeRatesDto fetchLatestRates() {
        log.debug("Fetching latest FX rates from TCMB: {}", tcmbUrl);

        try {
            String xmlResponse = restTemplate.getForObject(tcmbUrl, String.class);

            if (xmlResponse == null || xmlResponse.trim().isEmpty()) {
                throw new ExternalApiException("TCMB API returned empty response");
            }

            return parseTcmbXml(xmlResponse);

        } catch (HttpClientErrorException ex) {
            throw new ExternalApiException(
                    "TCMB API returned a client error: " + ex.getStatusCode(), ex);
        } catch (HttpServerErrorException ex) {
            throw new ExternalApiException(
                    "TCMB API is currently unavailable: " + ex.getStatusCode(), ex);
        } catch (ResourceAccessException ex) {
            throw new ExternalApiException(
                    "Failed to access TCMB API. Please check network connectivity.", ex);
        } catch (Exception ex) {
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
