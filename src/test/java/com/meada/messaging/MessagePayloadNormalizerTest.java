package com.meada.messaging;

import com.meada.messaging.NormalizedJid.JidType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários do {@link MessagePayloadNormalizer}. Lógica pura — sem Spring
 * context, sem I/O. Instancia direto e roda em milissegundos.
 */
class MessagePayloadNormalizerTest {

    private final MessagePayloadNormalizer normalizer = new MessagePayloadNormalizer();

    @Test
    @DisplayName("USER: número válido vira E.164 com + e preserva rawJid")
    void userValido() {
        var r = normalizer.normalize("5511999990000@s.whatsapp.net");
        assertThat(r.type()).isEqualTo(JidType.USER);
        assertThat(r.phoneNumber()).isEqualTo("+5511999990000");
        assertThat(r.rawJid()).isEqualTo("5511999990000@s.whatsapp.net");
    }

    @Test
    @DisplayName("USER: JID com sufixo de device (:N) descarta o device")
    void userComDevice() {
        var r = normalizer.normalize("5511999990000:12@s.whatsapp.net");
        assertThat(r.type()).isEqualTo(JidType.USER);
        assertThat(r.phoneNumber()).isEqualTo("+5511999990000");
    }

    @Test
    @DisplayName("USER: JID que já vem com + não duplica o prefixo")
    void userComMaisExistente() {
        var r = normalizer.normalize("+5511999990000@s.whatsapp.net");
        assertThat(r.type()).isEqualTo(JidType.USER);
        assertThat(r.phoneNumber()).isEqualTo("+5511999990000");
    }

    @Test
    @DisplayName("GROUP: @g.us classifica como GROUP sem phoneNumber")
    void grupo() {
        var r = normalizer.normalize("120363012345678901@g.us");
        assertThat(r.type()).isEqualTo(JidType.GROUP);
        assertThat(r.phoneNumber()).isNull();
        assertThat(r.rawJid()).isEqualTo("120363012345678901@g.us");
    }

    @Test
    @DisplayName("BROADCAST: @broadcast classifica como BROADCAST sem phoneNumber")
    void broadcast() {
        var r = normalizer.normalize("status@broadcast");
        assertThat(r.type()).isEqualTo(JidType.BROADCAST);
        assertThat(r.phoneNumber()).isNull();
    }

    @Test
    @DisplayName("UNKNOWN: sufixo @lid não-suportado")
    void lidNaoSuportado() {
        var r = normalizer.normalize("5511999990000@lid");
        assertThat(r.type()).isEqualTo(JidType.UNKNOWN);
        assertThat(r.phoneNumber()).isNull();
        assertThat(r.rawJid()).isEqualTo("5511999990000@lid");
    }

    @Test
    @DisplayName("UNKNOWN: sufixo futuro qualquer (@newsletter)")
    void sufixoFuturo() {
        var r = normalizer.normalize("123@newsletter");
        assertThat(r.type()).isEqualTo(JidType.UNKNOWN);
    }

    @Test
    @DisplayName("UNKNOWN: string sem @")
    void semArroba() {
        var r = normalizer.normalize("5511999990000");
        assertThat(r.type()).isEqualTo(JidType.UNKNOWN);
        assertThat(r.phoneNumber()).isNull();
    }

    @Test
    @DisplayName("UNKNOWN: caracteres não-numéricos no número de usuário")
    void usuarioNaoNumerico() {
        var r = normalizer.normalize("55ab999@s.whatsapp.net");
        assertThat(r.type()).isEqualTo(JidType.UNKNOWN);
        assertThat(r.phoneNumber()).isNull();
    }

    @Test
    @DisplayName("UNKNOWN: número curto demais (< 8 dígitos)")
    void numeroCurto() {
        var r = normalizer.normalize("123@s.whatsapp.net");
        assertThat(r.type()).isEqualTo(JidType.UNKNOWN);
    }

    @Test
    @DisplayName("UNKNOWN: número longo demais (> 15 dígitos)")
    void numeroLongo() {
        var r = normalizer.normalize("12345678901234567@s.whatsapp.net");
        assertThat(r.type()).isEqualTo(JidType.UNKNOWN);
    }

    @Test
    @DisplayName("UNKNOWN: entrada null não lança — classifica, rawJid null")
    void entradaNull() {
        var r = normalizer.normalize(null);
        assertThat(r.type()).isEqualTo(JidType.UNKNOWN);
        assertThat(r.phoneNumber()).isNull();
        assertThat(r.rawJid()).isNull();
    }

    @Test
    @DisplayName("UNKNOWN: string vazia/blank")
    void entradaVazia() {
        assertThat(normalizer.normalize("").type()).isEqualTo(JidType.UNKNOWN);
        assertThat(normalizer.normalize("   ").type()).isEqualTo(JidType.UNKNOWN);
    }

    @Test
    @DisplayName("USER: comprimento mínimo (8 dígitos) e máximo (15 dígitos) válidos")
    void limitesE164() {
        assertThat(normalizer.normalize("12345678@s.whatsapp.net").type()).isEqualTo(JidType.USER);
        assertThat(normalizer.normalize("123456789012345@s.whatsapp.net").type()).isEqualTo(JidType.USER);
    }
}
