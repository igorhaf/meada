package com.meada.whatsapp.messaging;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso de leitura a {@code whatsapp_instances}. Resolve o {@code instance_name}
 * do webhook para o tenant ({@code company_id}) — primeira etapa do fluxo: sem
 * resolver a instância, nenhuma escrita pode acontecer (FK aponta para company_id).
 *
 * <p>Read-only por design: instâncias são provisionadas via service_role fora do
 * webhook (decisão do schema). Este repositório só faz SELECT.
 */
@Repository
public class WhatsappInstanceRepository {

    /** SELECT com colunas explícitas — nunca SELECT *. Garante (em adição ao
     * column-grant do banco) que evolution_token nunca é lido aqui. */
    private static final String FIND_BY_NAME =
        "select id, company_id from whatsapp_instances where instance_name = ?";

    private static final RowMapper<WhatsappInstance> ROW_MAPPER = (rs, rowNum) ->
        new WhatsappInstance(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("company_id"));

    private final JdbcTemplate jdbcTemplate;

    public WhatsappInstanceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Resolve uma instância pelo nome.
     *
     * @return a instância, ou {@link Optional#empty()} se nenhuma tiver esse nome
     *         (instância desconhecida → o serviço responde 200 + log warning, por
     *         decisão de contrato: reentregar não resolve config ausente).
     */
    public Optional<WhatsappInstance> findByInstanceName(String instanceName) {
        Objects.requireNonNull(instanceName, "instanceName must not be null");
        // query() retorna lista vazia quando não há linha — sem exceção de
        // "não encontrado" (evita EmptyResultDataAccessException do queryForObject).
        return jdbcTemplate.query(FIND_BY_NAME, ROW_MAPPER, instanceName)
            .stream()
            .findFirst();
    }
}
