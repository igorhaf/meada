package com.meada.whatsapp.profiles.cursos.enrollments;

/**
 * Lançada quando o contato já tem uma matrícula ATIVA no mesmo curso (anti-dupla, camada 8.20 /
 * perfil cursos) — espelho da AlreadyActiveException do AcademiaMembershipService (camada 7.7), mas
 * por (contato, curso) em vez de por contato. → 409 already_enrolled no controller.
 */
public class AlreadyEnrolledException extends RuntimeException {
}
