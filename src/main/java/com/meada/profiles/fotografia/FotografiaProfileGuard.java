package com.meada.profiles.fotografia;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.profiles.CompanyProfileRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guard de perfil (camada 8.16 / perfil fotografia): garante que só um tenant do perfil 'fotografia'
 * acessa os endpoints do FotografiaBot. Lança {@link WrongProfileException} (→ 403
 * forbidden_wrong_profile) caso contrário. Clone do DermatologiaProfileGuard.
 */
@Component
public class FotografiaProfileGuard {

    private final CompanyProfileRepository companyProfileRepository;

    public FotografiaProfileGuard(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    /** Lançada quando o usuário não é um tenant do perfil fotografia. */
    public static class WrongProfileException extends RuntimeException {}

    /** Exige um tenant do perfil fotografia e devolve o companyId. Lança WrongProfileException senão. */
    public UUID requireFotografia(AuthenticatedUser user) {
        UUID companyId = user.companyId();
        if (companyId == null) {
            throw new WrongProfileException();
        }
        if (!"fotografia".equals(companyProfileRepository.findProfileId(companyId))) {
            throw new WrongProfileException();
        }
        return companyId;
    }
}
