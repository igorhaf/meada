package com.meada.whatsapp.profiles.modainfantil;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.profiles.CompanyProfileRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guard de perfil (camada 8.22 / perfil moda_infantil): garante que só um tenant do perfil
 * 'moda_infantil' acessa os endpoints do ModaInfantilBot. Defesa contra um tenant de outro perfil
 * tentar bater nas rotas /api/moda-infantil/**. Clone literal de
 * {@link com.meada.whatsapp.profiles.lingerie.LingerieProfileGuard} (chassi de varejo com variantes).
 *
 * <p>Lê company.profile_id via {@link CompanyProfileRepository} (da SM-A). Lança
 * {@link WrongProfileException} (→ 403 forbidden_wrong_profile no controller) quando o perfil não
 * casa ou o usuário não tem empresa (super-admin/INVITEE).
 */
@Component
public class ModaInfantilProfileGuard {

    private final CompanyProfileRepository companyProfileRepository;

    public ModaInfantilProfileGuard(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    /** Lançada quando o usuário não é um tenant do perfil moda_infantil. */
    public static class WrongProfileException extends RuntimeException {}

    /**
     * Exige que o usuário seja um tenant do perfil moda_infantil e devolve o companyId (conveniência —
     * os endpoints sempre precisam dele logo em seguida). Lança WrongProfileException caso contrário
     * (sem empresa, ou empresa de outro perfil).
     */
    public UUID requireModaInfantil(AuthenticatedUser user) {
        UUID companyId = user.companyId();
        if (companyId == null) {
            throw new WrongProfileException();
        }
        if (!"moda_infantil".equals(companyProfileRepository.findProfileId(companyId))) {
            throw new WrongProfileException();
        }
        return companyId;
    }
}
