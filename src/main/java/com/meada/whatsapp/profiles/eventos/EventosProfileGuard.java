package com.meada.whatsapp.profiles.eventos;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.profiles.CompanyProfileRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guard de perfil (camada 8.2): garante que só um tenant do perfil 'eventos' acessa os endpoints
 * do EventosBot. Lança {@link WrongProfileException} (→ 403 forbidden_wrong_profile) caso contrário.
 * Espelho do OficinaProfileGuard.
 */
@Component
public class EventosProfileGuard {

    private final CompanyProfileRepository companyProfileRepository;

    public EventosProfileGuard(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    /** Lançada quando o usuário não é um tenant do perfil eventos. */
    public static class WrongProfileException extends RuntimeException {}

    /** Exige um tenant do perfil eventos e devolve o companyId. Lança WrongProfileException senão. */
    public UUID requireEventos(AuthenticatedUser user) {
        UUID companyId = user.companyId();
        if (companyId == null) {
            throw new WrongProfileException();
        }
        if (!"eventos".equals(companyProfileRepository.findProfileId(companyId))) {
            throw new WrongProfileException();
        }
        return companyId;
    }
}
