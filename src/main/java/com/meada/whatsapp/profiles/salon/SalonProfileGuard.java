package com.meada.whatsapp.profiles.salon;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.profiles.CompanyProfileRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guard de perfil (camada 7.5): garante que só um tenant do perfil 'salon' acessa os endpoints do
 * SalãoBot. Defesa contra um tenant de outro perfil bater nas rotas /api/salon/**.
 *
 * <p>Lê company.profile_id via {@link CompanyProfileRepository} (da SM-A). Lança
 * {@link WrongProfileException} (→ 403 forbidden_wrong_profile no controller) quando o perfil não
 * casa ou o usuário não tem empresa (super-admin/INVITEE).
 */
@Component
public class SalonProfileGuard {

    private final CompanyProfileRepository companyProfileRepository;

    public SalonProfileGuard(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    /** Lançada quando o usuário não é um tenant do perfil salon. */
    public static class WrongProfileException extends RuntimeException {}

    /** Exige um tenant do perfil salon e devolve o companyId. Lança WrongProfileException senão. */
    public UUID requireSalon(AuthenticatedUser user) {
        UUID companyId = user.companyId();
        if (companyId == null) {
            throw new WrongProfileException();
        }
        if (!"salon".equals(companyProfileRepository.findProfileId(companyId))) {
            throw new WrongProfileException();
        }
        return companyId;
    }
}
