package com.meada.whatsapp.profiles.lavanderia;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.profiles.CompanyProfileRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guard de perfil (camada 8.10): garante que só um tenant do perfil 'lavanderia' acessa os endpoints do
 * LavanderiaBot. Defesa contra um tenant de outro perfil tentar bater nas rotas /api/lavanderia/**.
 * Clone literal de {@link com.meada.whatsapp.profiles.floricultura.FloriculturaProfileGuard}.
 *
 * <p>Lê company.profile_id via {@link CompanyProfileRepository}. Lança {@link WrongProfileException}
 * (→ 403 forbidden_wrong_profile no controller) quando o perfil não casa ou o usuário não tem empresa.
 */
@Component
public class LavanderiaProfileGuard {

    private final CompanyProfileRepository companyProfileRepository;

    public LavanderiaProfileGuard(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    /** Lançada quando o usuário não é um tenant do perfil lavanderia. */
    public static class WrongProfileException extends RuntimeException {}

    /**
     * Exige que o usuário seja um tenant do perfil lavanderia e devolve o companyId. Lança
     * WrongProfileException caso contrário (sem empresa, ou empresa de outro perfil).
     */
    public UUID requireLavanderia(AuthenticatedUser user) {
        UUID companyId = user.companyId();
        if (companyId == null) {
            throw new WrongProfileException();
        }
        if (!"lavanderia".equals(companyProfileRepository.findProfileId(companyId))) {
            throw new WrongProfileException();
        }
        return companyId;
    }
}
