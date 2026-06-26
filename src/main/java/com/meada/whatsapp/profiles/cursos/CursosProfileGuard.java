package com.meada.whatsapp.profiles.cursos;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.profiles.CompanyProfileRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guard de perfil (camada 8.20 / perfil cursos): garante que só um tenant do perfil 'cursos' acessa
 * os endpoints do CursosBot. Lança {@link WrongProfileException} (→ 403 forbidden_wrong_profile) caso
 * contrário. Clone do AcademiaProfileGuard (camada 7.7).
 */
@Component
public class CursosProfileGuard {

    private final CompanyProfileRepository companyProfileRepository;

    public CursosProfileGuard(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    /** Lançada quando o usuário não é um tenant do perfil cursos. */
    public static class WrongProfileException extends RuntimeException {}

    /** Exige um tenant do perfil cursos e devolve o companyId. Lança WrongProfileException senão. */
    public UUID requireCursos(AuthenticatedUser user) {
        UUID companyId = user.companyId();
        if (companyId == null) {
            throw new WrongProfileException();
        }
        if (!"cursos".equals(companyProfileRepository.findProfileId(companyId))) {
            throw new WrongProfileException();
        }
        return companyId;
    }
}
