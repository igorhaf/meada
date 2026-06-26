package com.meada.whatsapp.profiles.otica;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.profiles.CompanyProfileRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guard de perfil (camada 8.12, perfil otica — PRIMEIRO HÍBRIDO): garante que só um tenant do perfil
 * 'otica' acessa os endpoints {@code /api/otica/**} (FLUXO A — exames + FLUXO B — encomendas). Defesa
 * contra um tenant de outro perfil bater nas rotas. Clone literal de
 * {@link com.meada.whatsapp.profiles.dental.DentalProfileGuard} /
 * {@link com.meada.whatsapp.profiles.floricultura.FloriculturaProfileGuard}.
 *
 * <p>Lê company.profile_id via {@link CompanyProfileRepository} (da SM-A). Lança
 * {@link WrongProfileException} (→ 403 forbidden_wrong_profile no controller) quando o perfil não
 * casa ou o usuário não tem empresa (super-admin/INVITEE).
 */
@Component
public class OticaProfileGuard {

    private final CompanyProfileRepository companyProfileRepository;

    public OticaProfileGuard(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    /** Lançada quando o usuário não é um tenant do perfil otica. */
    public static class WrongProfileException extends RuntimeException {}

    /**
     * Exige que o usuário seja um tenant do perfil otica e devolve o companyId (conveniência — os
     * endpoints sempre precisam dele logo em seguida). Lança WrongProfileException caso contrário.
     */
    public UUID requireOtica(AuthenticatedUser user) {
        UUID companyId = user.companyId();
        if (companyId == null) {
            throw new WrongProfileException();
        }
        if (!"otica".equals(companyProfileRepository.findProfileId(companyId))) {
            throw new WrongProfileException();
        }
        return companyId;
    }
}
