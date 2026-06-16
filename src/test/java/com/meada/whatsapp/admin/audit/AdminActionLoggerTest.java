package com.meada.whatsapp.admin.audit;

import com.meada.whatsapp.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test do {@link AdminActionLogger} contra PostgreSQL real. Verifica gravação
 * com payload jsonb e busca por super_admin + período.
 */
class AdminActionLoggerTest extends AbstractIntegrationTest {

    @Autowired
    private AdminActionLogger logger;

    @Test
    @DisplayName("log: grava ação com payload jsonb recuperável")
    void log_persistsWithJsonbPayload() {
        UUID admin = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        logger.log(admin, AdminAction.COMPANY_SUSPENDED, AdminAction.TARGET_COMPANY, target,
            Map.of("reason", "inadimplência", "by", "smoke"));

        Map<String, Object> row = jdbcTemplate.queryForMap(
            "select action, target_type, target_id, payload->>'reason' as reason "
                + "from admin_action_log where super_admin_user_id = ?", admin);
        assertThat(row.get("action")).isEqualTo(AdminAction.COMPANY_SUSPENDED);
        assertThat(row.get("target_type")).isEqualTo("company");
        assertThat(row.get("target_id")).isEqualTo(target);
        assertThat(row.get("reason")).isEqualTo("inadimplência");
    }

    @Test
    @DisplayName("busca por super_admin_id retorna as ações daquele admin, ordenáveis por período")
    void queryByActorAndPeriod() {
        UUID admin = UUID.randomUUID();
        logger.log(admin, AdminAction.USER_SUSPENDED, AdminAction.TARGET_USER, UUID.randomUUID(), null);
        logger.log(admin, AdminAction.USER_REACTIVATED, AdminAction.TARGET_USER, UUID.randomUUID(), Map.of());

        List<String> actions = jdbcTemplate.queryForList(
            "select action from admin_action_log where super_admin_user_id = ? "
                + "and created_at >= now() - interval '1 hour' order by created_at desc",
            String.class, admin);
        assertThat(actions).containsExactlyInAnyOrder(
            AdminAction.USER_SUSPENDED, AdminAction.USER_REACTIVATED);
    }
}
