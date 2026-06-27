package com.meada.profiles.restaurant;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.profiles.CompanyProfileRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guard de perfil (camada 7.3): garante que só um tenant do perfil 'restaurant' acessa os
 * endpoints do MesaBot. Defesa contra um tenant generic/legal/dental/sushi tentar bater nas
 * rotas /api/restaurant/**.
 *
 * <p>Lê company.profile_id via {@link CompanyProfileRepository} (da SM-A). Lança
 * {@link WrongProfileException} (→ 403 forbidden_wrong_profile no controller) quando o perfil
 * não casa ou o usuário não tem empresa (super-admin/INVITEE).
 */
@Component
public class RestaurantProfileGuard {

    private final CompanyProfileRepository companyProfileRepository;

    public RestaurantProfileGuard(CompanyProfileRepository companyProfileRepository) {
        this.companyProfileRepository = companyProfileRepository;
    }

    /** Lançada quando o usuário não é um tenant do perfil restaurant. */
    public static class WrongProfileException extends RuntimeException {}

    /**
     * Exige que o usuário seja um tenant do perfil restaurant e devolve o companyId (conveniência —
     * os endpoints sempre precisam dele logo em seguida). Lança WrongProfileException caso contrário
     * (sem empresa, ou empresa de outro perfil).
     */
    public UUID requireRestaurant(AuthenticatedUser user) {
        UUID companyId = user.companyId();
        if (companyId == null) {
            throw new WrongProfileException();
        }
        if (!"restaurant".equals(companyProfileRepository.findProfileId(companyId))) {
            throw new WrongProfileException();
        }
        return companyId;
    }
}
