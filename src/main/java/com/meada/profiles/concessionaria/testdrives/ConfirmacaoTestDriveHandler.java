package com.meada.profiles.concessionaria.testdrives;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <confirmacao_testdrive>{...}</confirmacao_testdrive>} da resposta da IA e MUTA
 * o status do test-drive (onda 1 da concessionária, backlog #3 — fecha o loop do lembrete "confirma? SIM/CANCELAR").
 * A IA só REFLETE a decisão do CLIENTE: {@code decisao} confirmado (só de 'agendado') ou cancelado
 * (de 'agendado'/'confirmado' — a máquina de status valida; cancelar LIBERA o slot na hora).
 *
 * <p>BARREIRA DE CONTATO: o test-drive tem de pertencer ao MESMO contato da conversa (impede mexer
 * no horário de outro cliente — espelho da entrega de plano do nutri). Transição inválida, id
 * inexistente, decisão desconhecida ou contato divergente → {@link Optional#empty()} + warn (a
 * mensagem da IA segue sem mutação). O OutboundService remove a tag antes de enviar.
 */
@Component
public class ConfirmacaoTestDriveHandler {

    private static final Logger log = LoggerFactory.getLogger(ConfirmacaoTestDriveHandler.class);

    private static final Pattern TAG = Pattern.compile(
        "<confirmacao_testdrive>\\s*(\\{.*?\\})\\s*</confirmacao_testdrive>", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final ConcessionariaTestDriveService testDriveService;

    public ConfirmacaoTestDriveHandler(ObjectMapper objectMapper,
                                       ConcessionariaTestDriveService testDriveService) {
        this.objectMapper = objectMapper;
        this.testDriveService = testDriveService;
    }

    public boolean hasConfirmacaoTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    public String stripConfirmacaoTag(String text) {
        if (text == null) {
            return null;
        }
        return TAG.matcher(text).replaceAll("").stripTrailing();
    }

    /**
     * Extrai a tag e aplica a decisão do cliente ao test-drive. {@link Optional#empty()} quando:
     * não há tag, JSON/campos inválidos, test-drive inexistente, contato divergente (barreira) ou
     * transição inválida na máquina de status.
     */
    public Optional<ConcessionariaTestDrive> parseAndApply(UUID companyId, UUID conversationId, UUID contactId,
                                                     String aiResponseText) {
        if (aiResponseText == null) {
            return Optional.empty();
        }
        Matcher m = TAG.matcher(aiResponseText);
        if (!m.find()) {
            return Optional.empty();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(m.group(1));
        } catch (Exception e) {
            log.warn("concessionaria: tag <confirmacao_testdrive> com JSON inválido p/ conversa {} ({})",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String rawId = root.path("test_drive_id").asText(null);
        String decisao = root.path("decisao").asText(null);
        if (rawId == null || (!"confirmado".equals(decisao) && !"cancelado".equals(decisao))) {
            log.warn("concessionaria: tag <confirmacao_testdrive> com campos inválidos p/ conversa {}", conversationId);
            return Optional.empty();
        }
        UUID testDriveId;
        try {
            testDriveId = UUID.fromString(rawId);
        } catch (IllegalArgumentException e) {
            log.warn("concessionaria: <confirmacao_testdrive> com test_drive_id inválido p/ conversa {}", conversationId);
            return Optional.empty();
        }

        Optional<ConcessionariaTestDrive> current = testDriveService.get(companyId, testDriveId);
        if (current.isEmpty()) {
            log.warn("concessionaria: <confirmacao_testdrive> p/ test-drive inexistente {} (conversa {})",
                testDriveId, conversationId);
            return Optional.empty();
        }
        // BARREIRA DE CONTATO: só o dono do test-drive confirma/cancela pela conversa.
        if (current.get().contactId() == null || !current.get().contactId().equals(contactId)) {
            log.warn("concessionaria: <confirmacao_testdrive> de contato divergente p/ test-drive {} (conversa {}) — ignorada",
                testDriveId, conversationId);
            return Optional.empty();
        }

        try {
            ConcessionariaTestDrive updated = testDriveService.updateStatus(companyId, testDriveId, decisao);
            log.info("concessionaria: test-drive {} → {} pela resposta do cliente (conversa {})",
                testDriveId, decisao, conversationId);
            return Optional.of(updated);
        } catch (ConcessionariaTestDriveService.InvalidStatusTransitionException e) {
            log.warn("concessionaria: <confirmacao_testdrive> com transição inválida p/ test-drive {} (status atual não permite {})",
                testDriveId, decisao);
            return Optional.empty();
        } catch (RuntimeException e) {
            log.warn("concessionaria: falha ao aplicar <confirmacao_testdrive> p/ test-drive {} ({})",
                testDriveId, e.getMessage());
            return Optional.empty();
        }
    }
}
