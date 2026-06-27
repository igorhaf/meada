package com.meada.profiles.sushi.loyalty;

import com.meada.AbstractIntegrationTest;
import com.meada.profiles.sushi.loyalty.SushiLoyaltyConfigService.InvalidLoyaltyConfigException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o SushiLoyaltyConfigService (camada 7.1 / sushi funcional): get com fallback p/ defaults
 * (enabled=false), update (upsert) e validação do reward.
 */
class SushiLoyaltyConfigServiceTest extends AbstractIntegrationTest {

    @Autowired
    private SushiLoyaltyConfigService service;

    private static final UUID COMPANY = UUID.fromString("c8d00000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("d8d00000-0000-0000-0000-000000000001");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'sushi')",
            COMPANY, "Sushi Loy", "sushi-loy");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@sushi-loy.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("get sem linha → defaults (enabled=false)")
    void getFallback() {
        SushiLoyaltyConfig cfg = service.get(COMPANY);
        assertThat(cfg.enabled()).isFalse();
        assertThat(cfg.companyId()).isEqualTo(COMPANY);
    }

    @Test
    @DisplayName("update (upsert) grava e re-update altera")
    void upsert() {
        SushiLoyaltyConfig saved = service.update(COMPANY, USER, true, 10, "percent", 15);
        assertThat(saved.enabled()).isTrue();
        assertThat(saved.thresholdOrders()).isEqualTo(10);
        assertThat(saved.rewardValue()).isEqualTo(15);

        SushiLoyaltyConfig again = service.update(COMPANY, USER, false, 5, "fixed", 1000);
        assertThat(again.enabled()).isFalse();
        assertThat(again.rewardKind()).isEqualTo("fixed");
        assertThat(again.rewardValue()).isEqualTo(1000);
    }

    @Test
    @DisplayName("reward percent fora de 0..100 → InvalidLoyaltyConfigException")
    void invalidReward() {
        assertThatThrownBy(() -> service.update(COMPANY, USER, true, 10, "percent", 101))
            .isInstanceOf(InvalidLoyaltyConfigException.class);
    }

    @Test
    @DisplayName("threshold < 1 → InvalidLoyaltyConfigException")
    void invalidThreshold() {
        assertThatThrownBy(() -> service.update(COMPANY, USER, true, 0, "percent", 10))
            .isInstanceOf(InvalidLoyaltyConfigException.class);
    }
}
