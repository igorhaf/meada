package com.meada.whatsapp.profiles.dental;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.profiles.CompanyProfileRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guard de perfil (camada 7.4): garante que só um tenant do perfil 'dental' acessa os endpoints
 * do DentalBot. Defesa contra um tenant de outro perfil bater nas rotas /api/dental/**.
 *
 * <p>Lê company.profile_id via {@link CompanyProfileRepository} (da SM-A). Lança
 * {@link WrongProfileException} (→ 403 forbidden_wrong_profile no controller) quando o perfil
 * não casa ou o usuário não tem empresa (super-admin/INVITEE).
 */
@Component
public class DentalProfileGuard {

    private final CompanyProfileRepository companyProfileRepository;

    public DentalProfileGuard(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    /** Lançada quando o usuário não é um tenant do perfil dental. */
    public static class WrongProfileException extends RuntimeException {}

    /**
     * Exige que o usuário seja um tenant do perfil dental e devolve o companyId (conveniência —
     * os endpoints sempre precisam dele logo em seguida). Lança WrongProfileException caso contrário.
     */
    public UUID requireDental(AuthenticatedUser user) {
        UUID companyId = user.companyId();
        if (companyId == null) {
            throw new WrongProfileException();
        }
        if (!"dental".equals(companyProfileRepository.findProfileId(companyId))) {
            throw new WrongProfileException();
        }
        return companyId;
    }
}
