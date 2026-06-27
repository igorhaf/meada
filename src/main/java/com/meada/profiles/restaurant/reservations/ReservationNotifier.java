package com.meada.profiles.restaurant.reservations;

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
 * Dispara a notificação outbound ao cliente quando a reserva muda de status (camada 7.3,
 * decisão 3). Espelho do SushiOrderNotifier: resolve telefone + credenciais via a conversa e
 * envia pela Evolution. EVOLUTION_DRY_RUN (dev) é honrado pelo EvolutionSender (loga em vez de
 * enviar).
 *
 * <p>Best-effort por contrato: falha de envio NUNCA reverte a transição de status (já persistida)
 * — loga warn e segue. A mensagem é persistida em {@code messages} (outbound/human — é o restaurante
 * avisando, não a IA), best-effort.
 *
 * <p>Quando {@code conversationId} é null (reserva criada manualmente pelo tenant via API, sem
 * WhatsApp), pula em silêncio — não há canal para notificar.
 */
@Component
public class ReservationNotifier {

    private static final Logger log = LoggerFactory.getLogger(ReservationNotifier.class);

    private final ConversationRepository conversationRepository;
    private final ContactRepository contactRepository;
    private final WhatsappInstanceRepository whatsappInstanceRepository;
    private final MessageRepository messageRepository;
    private final EvolutionSender evolutionSender;

    public ReservationNotifier(ConversationRepository conversationRepository,
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

    /** Envia o texto do status ao cliente da conversa. Best-effort (nunca lança). */
    public void notifyStatus(UUID companyId, UUID conversationId, String text) {
        if (text == null) {
            return;   // status que não notifica (pendente/realizada/no_show).
        }
        if (conversationId == null) {
            return;   // reserva manual sem WhatsApp — nada a notificar (silencioso).
        }
        try {
            Optional<String> phone = contactRepository.findPhoneByConversationId(conversationId);
            Optional<UUID> instanceId = conversationRepository.findInstanceIdByConversation(conversationId);
            Optional<EvolutionCredentials> creds = instanceId
                .flatMap(whatsappInstanceRepository::findEvolutionCredentials);
            if (phone.isEmpty() || phone.get().isBlank() || creds.isEmpty()) {
                log.warn("restaurant: reserva sem canal resolúvel (phone/creds) p/ conversa {} — notificação não enviada",
                    conversationId);
                return;
            }
            String keyId = evolutionSender.sendText(
                creds.get().instanceName(), creds.get().token(), phone.get(), text);
            messageRepository.insertIfNew(companyId, conversationId,
                MessageDirection.OUTBOUND, MessageSender.HUMAN, text, keyId);
        } catch (RuntimeException e) {
            log.warn("restaurant: falha ao notificar status p/ conversa {} ({}) — reserva segue mesmo assim",
                conversationId, e.getMessage());
        }
    }
}
