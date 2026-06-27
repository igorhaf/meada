package com.meada.profiles.oficina;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.profiles.CompanyProfileRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guard de perfil (camada 7.9): garante que só um tenant do perfil 'oficina' acessa os endpoints
 * do OficinaBot. Lança {@link WrongProfileException} (→ 403 forbidden_wrong_profile) caso contrário.
 */
@Component
public class OficinaProfileGuard {

    private final CompanyProfileRepository companyProfileRepository;

    public OficinaProfileGuard(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    /** Lançada quando o usuário não é um tenant do perfil oficina. */
    public static class WrongProfileException extends RuntimeException {}

    /** Exige um tenant do perfil oficina e devolve o companyId. Lança WrongProfileException senão. */
    public UUID requireOficina(AuthenticatedUser user) {
        UUID companyId = user.companyId();
        if (companyId == null) {
            throw new WrongProfileException();
        }
        if (!"oficina".equals(companyProfileRepository.findProfileId(companyId))) {
            throw new WrongProfileException();
        }
        return companyId;
    }
}
