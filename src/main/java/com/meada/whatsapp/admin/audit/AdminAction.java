package com.meada.whatsapp.admin.audit;

/**
 * Constantes das ações do super-admin registradas no admin_action_log (camada 6).
 * Strings estáveis (gravadas no banco) — não renomear sem migração de dados.
 */
public final class AdminAction {
    private AdminAction() {}

    public static final String COMPANY_SUSPENDED = "COMPANY_SUSPENDED";
    public static final String COMPANY_REACTIVATED = "COMPANY_REACTIVATED";
    public static final String COMPANY_UPDATED = "COMPANY_UPDATED";
    public static final String COMPANY_DELETED = "COMPANY_DELETED";
    public static final String USER_SUSPENDED = "USER_SUSPENDED";
    public static final String USER_REACTIVATED = "USER_REACTIVATED";
    public static final String USER_PASSWORD_RESET = "USER_PASSWORD_RESET";
    public static final String USER_DELETED = "USER_DELETED";
    public static final String INVITATION_REVOKED = "INVITATION_REVOKED";
    public static final String NOTE_CREATED = "NOTE_CREATED";
    public static final String NOTE_UPDATED = "NOTE_UPDATED";
    public static final String NOTE_DELETED = "NOTE_DELETED";

    /** Tipos de alvo (target_type). */
    public static final String TARGET_COMPANY = "company";
    public static final String TARGET_USER = "user";
    public static final String TARGET_INVITATION = "invitation";
    public static final String TARGET_NOTE = "note";
}
