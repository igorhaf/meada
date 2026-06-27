package com.meada.profiles.cursos.enrollments;

import com.meada.messaging.ContactRepository;
import com.meada.messaging.ConversationRepository;
import com.meada.messaging.EvolutionCredentials;
import com.meada.messaging.MessageDirection;
import com.meada.messaging.MessageRepository;
import com.meada.messaging.MessageSender;
import com.meada.messaging.WhatsappInstanceRepository;
import com.meada.outbound.EvolutionSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Dispara a notificação outbound ao aluno quando a matrícula muda de status (camada 8.20 / perfil
 * cursos) — clone do AcademiaMembershipNotifier (camada 7.7). Best-effort: falha NUNCA reverte.
 * Persiste em {@code messages} (outbound/human). conversationId null → skip. {@link #sendText} é usado
 * pela ENTREGA do módulo (content VERBATIM, espelho do FotografiaAppointmentNotifier.sendText). Texto
 * defensivo, SEM promessa de resultado.
 */
@Component
public class CursosEnrollmentNotifier {

    private static final Logger log = LoggerFactory.getLogger(CursosEnrollmentNotifier.class);

    private final ConversationRepository conversationRepository;
    private final ContactRepository contactRepository;
    private final WhatsappInstanceRepository whatsappInstanceRepository;
    private final MessageRepository messageRepository;
    private final EvolutionSender evolutionSender;

    public CursosEnrollmentNotifier(ConversationRepository conversationRepository,
                                    ContactRepository contactRepository,
                                    WhatsappInstanceRepository whatsappInstanceRepository,
                                    MessageRepository messageRepository,
                                    EvolutionSender evolutionSender) {
        this.conversationRepository = conversationRepository;
        this.contactRepository = contactRepository;
        this.whatsappInstanceRepository = whatsappInstanceRepository;
        this.messageRepository = messageRepository;
        this.evolutionSender = evolutionSender;
    }

    public void notifyStatus(UUID companyId, UUID conversationId, String text) {
        if (text == null || conversationId == null) {
            return;
        }
        sendInternal(companyId, conversationId, text, "notificar status");
    }

    /** Envia um texto outbound arbitrário (usado pela ENTREGA do módulo — o content exato VERBATIM). */
    public boolean sendText(UUID companyId, UUID conversationId, String text) {
        if (text == null || conversationId == null) {
            return false;
        }
        return sendInternal(companyId, conversationId, text, "entregar módulo");
    }

    private boolean sendInternal(UUID companyId, UUID conversationId, String text, String op) {
        try {
            Optional<String> phone = contactRepository.findPhoneByConversationId(conversationId);
            Optional<UUID> instanceId = conversationRepository.findInstanceIdByConversation(conversationId);
            Optional<EvolutionCredentials> creds = instanceId
                .flatMap(whatsappInstanceRepository::findEvolutionCredentials);
            if (phone.isEmpty() || phone.get().isBlank() || creds.isEmpty()) {
                log.warn("cursos: matrícula sem canal resolúvel (phone/creds) p/ conversa {} — {} não enviado",
                    conversationId, op);
                return false;
            }
            String keyId = evolutionSender.sendText(
                creds.get().instanceName(), creds.get().token(), phone.get(), text);
            messageRepository.insertIfNew(companyId, conversationId,
                MessageDirection.OUTBOUND, MessageSender.HUMAN, text, keyId);
            return true;
        } catch (RuntimeException e) {
            log.warn("cursos: falha ao {} p/ conversa {} ({}) — matrícula segue mesmo assim",
                op, conversationId, e.getMessage());
            return false;
        }
    }
}
