package com.meada.profiles.barbearia;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.profiles.CompanyProfileRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guard de perfil (camada 8.1): garante que só um tenant do perfil 'barbearia' acessa os endpoints do
 * BarbeariaBot. Defesa contra um tenant de outro perfil bater nas rotas /api/barbearia/**.
 *
 * <p>Lê company.profile_id via {@link CompanyProfileRepository} (da SM-A). Lança
 * {@link WrongProfileException} (→ 403 forbidden_wrong_profile no controller) quando o perfil não
 * casa ou o usuário não tem empresa (super-admin/INVITEE).
 */
@Component
public class BarberProfileGuard {

    private final CompanyProfileRepository companyProfileRepository;

    public BarberProfileGuard(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    /** Lançada quando o usuário não é um tenant do perfil barbearia. */
    public static class WrongProfileException extends RuntimeException {}

    /** Exige um tenant do perfil barbearia e devolve o companyId. Lança WrongProfileException senão. */
    public UUID requireBarbearia(AuthenticatedUser user) {
        UUID companyId = user.companyId();
        if (companyId == null) {
            throw new WrongProfileException();
        }
        if (!"barbearia".equals(companyProfileRepository.findProfileId(companyId))) {
            throw new WrongProfileException();
        }
        return companyId;
    }
}
