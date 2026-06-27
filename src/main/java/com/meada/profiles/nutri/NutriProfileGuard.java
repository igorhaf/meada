package com.meada.profiles.nutri;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.profiles.CompanyProfileRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guard de perfil (camada 8.0): garante que só um tenant do perfil 'nutri' acessa os endpoints do
 * NutriBot. Lança {@link WrongProfileException} (→ 403 forbidden_wrong_profile) caso contrário.
 */
@Component
public class NutriProfileGuard {

    private final CompanyProfileRepository companyProfileRepository;

    public NutriProfileGuard(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    /** Lançada quando o usuário não é um tenant do perfil nutri. */
    public static class WrongProfileException extends RuntimeException {}

    /** Exige um tenant do perfil nutri e devolve o companyId. Lança WrongProfileException senão. */
    public UUID requireNutri(AuthenticatedUser user) {
        UUID companyId = user.companyId();
        if (companyId == null) {
            throw new WrongProfileException();
        }
        if (!"nutri".equals(companyProfileRepository.findProfileId(companyId))) {
            throw new WrongProfileException();
        }
        return companyId;
    }
}
