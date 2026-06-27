package com.meada.profiles.lingerie;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.profiles.CompanyProfileRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guard de perfil (camada 8.21 / perfil lingerie): garante que só um tenant do perfil 'lingerie'
 * acessa os endpoints do LingerieBot. Defesa contra um tenant de outro perfil tentar bater nas rotas
 * /api/lingerie/**. Clone literal de {@link com.meada.profiles.adega.AdegaProfileGuard}.
 *
 * <p>Lê company.profile_id via {@link CompanyProfileRepository} (da SM-A). Lança
 * {@link WrongProfileException} (→ 403 forbidden_wrong_profile no controller) quando o perfil não
 * casa ou o usuário não tem empresa (super-admin/INVITEE).
 */
@Component
public class LingerieProfileGuard {

    private final CompanyProfileRepository companyProfileRepository;

    public LingerieProfileGuard(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    /** Lançada quando o usuário não é um tenant do perfil lingerie. */
    public static class WrongProfileException extends RuntimeException {}

    /**
     * Exige que o usuário seja um tenant do perfil lingerie e devolve o companyId (conveniência — os
     * endpoints sempre precisam dele logo em seguida). Lança WrongProfileException caso contrário
     * (sem empresa, ou empresa de outro perfil).
     */
    public UUID requireLingerie(AuthenticatedUser user) {
        UUID companyId = user.companyId();
        if (companyId == null) {
            throw new WrongProfileException();
        }
        if (!"lingerie".equals(companyProfileRepository.findProfileId(companyId))) {
            throw new WrongProfileException();
        }
        return companyId;
    }
}
