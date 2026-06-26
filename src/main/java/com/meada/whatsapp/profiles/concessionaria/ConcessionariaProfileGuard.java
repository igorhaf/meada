package com.meada.whatsapp.profiles.concessionaria;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.profiles.CompanyProfileRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guard de perfil (camada 8.17): garante que só um tenant do perfil 'concessionaria' acessa os
 * endpoints do ConcessionariaBot. Lança {@link WrongProfileException} (→ 403 forbidden_wrong_profile)
 * caso contrário. Espelho do DentalProfileGuard/OficinaProfileGuard.
 */
@Component
public class ConcessionariaProfileGuard {

    private final CompanyProfileRepository companyProfileRepository;

    public ConcessionariaProfileGuard(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    /** Lançada quando o usuário não é um tenant do perfil concessionaria. */
    public static class WrongProfileException extends RuntimeException {}

    /** Exige um tenant do perfil concessionaria e devolve o companyId. Lança WrongProfileException senão. */
    public UUID requireConcessionaria(AuthenticatedUser user) {
        UUID companyId = user.companyId();
        if (companyId == null) {
            throw new WrongProfileException();
        }
        if (!"concessionaria".equals(companyProfileRepository.findProfileId(companyId))) {
            throw new WrongProfileException();
        }
        return companyId;
    }
}
