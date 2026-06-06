package com.finance.portal.market.application.crypto;

import com.finance.portal.news.application.port.TranslationPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CryptoDescriptionTranslationServiceTest {

    @Mock
    private TranslationPort translationPort;

    @Test
    void translateToTurkish_delegatesEnToTr() {
        when(translationPort.translate("RAIN is a token. <a href=\"x\">link</a>", "en", "tr"))
                .thenReturn("RAIN bir token'dır. <a href=\"x\">bağlantı</a>");
        CryptoDescriptionTranslationService svc = new CryptoDescriptionTranslationService(translationPort);

        String out = svc.translateToTurkish("rain", "RAIN is a token. <a href=\"x\">link</a>");

        assertThat(out).isEqualTo("RAIN bir token'dır. <a href=\"x\">bağlantı</a>");
    }

    @Test
    void translateToTurkish_blankInput_returnsAsIsWithoutCallingPort() {
        CryptoDescriptionTranslationService svc = new CryptoDescriptionTranslationService(translationPort);

        assertThat(svc.translateToTurkish("rain", "  ")).isEqualTo("  ");
        verifyNoInteractions(translationPort);
    }

    @Test
    void translateToTurkish_translationThrows_fallsBackToEnglish() {
        when(translationPort.translate("English text", "en", "tr"))
                .thenThrow(new RuntimeException("quota exceeded"));
        CryptoDescriptionTranslationService svc = new CryptoDescriptionTranslationService(translationPort);

        String out = svc.translateToTurkish("rain", "English text");

        assertThat(out).isEqualTo("English text");
    }

    @Test
    void translateToTurkish_blankTranslation_fallsBackToEnglish() {
        when(translationPort.translate("English text", "en", "tr")).thenReturn("   ");
        CryptoDescriptionTranslationService svc = new CryptoDescriptionTranslationService(translationPort);

        String out = svc.translateToTurkish("rain", "English text");

        assertThat(out).isEqualTo("English text");
    }
}
