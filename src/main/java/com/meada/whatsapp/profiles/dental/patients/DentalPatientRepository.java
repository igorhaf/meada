package com.meada.whatsapp.profiles.dental.patients;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code dental_patients} (camada 7.4). Opera via service_role; o escopo por company_id
 * no WHERE de cada query é a defesa. {@link #findByContactId} é o ponto de entrada da IA
 * (resolve contact → patient).
 */
@Repository
public class DentalPatientRepository {

    private static final RowMapper<DentalPatient> MAPPER = (rs, rn) -> new DentalPatient(
        (UUID) rs.getObject("id"),
        rs.getString("name"),
        rs.getString("email"),
        rs.getString("phone"),
        rs.getString("document"),
        rs.getObject("birth_date", LocalDate.class),
        (UUID) rs.getObject("contact_id"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS =
        "id, name, email, phone, document, birth_date, contact_id, notes, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public DentalPatientRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Lista pacientes do tenant, com busca opcional (nome/email/telefone/CPF). */
    public List<DentalPatient> listByCompany(UUID companyId, String search) {
        StringBuilder sql = new StringBuilder(
            "select " + COLS + " from dental_patients where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (search != null && !search.isBlank()) {
            sql.append(" and (name ilike ? or email ilike ? or phone ilike ? or document ilike ?)");
            String like = "%" + search.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql.append(" order by name asc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public Optional<DentalPatient> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from dental_patients where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    /** Resolve o paciente ligado a um contato WhatsApp (usado pela IA). Empty se não houver vínculo. */
    public Optional<DentalPatient> findByContactId(UUID companyId, UUID contactId) {
        if (contactId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                "select " + COLS + " from dental_patients where company_id = ? and contact_id = ? "
                    + "order by created_at asc limit 1",
                MAPPER, companyId, contactId)
            .stream().findFirst();
    }

    public DentalPatient insert(UUID companyId, String name, String email, String phone,
                                String document, LocalDate birthDate, UUID contactId, String notes) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into dental_patients (company_id, name, email, phone, document, birth_date, contact_id, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, name.trim(), email, phone, document,
            birthDate == null ? null : Date.valueOf(birthDate), contactId, notes);
        return findById(companyId, id).orElseThrow();
    }

    /** Atualiza campos não-null (PATCH parcial). Retorna empty se não existir/pertencer ao tenant. */
    public Optional<DentalPatient> update(UUID companyId, UUID id, String name, String email,
                                          String phone, String document, LocalDate birthDate,
                                          UUID contactId, String notes) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        // email/phone/document/notes: null = não mexe; string vazia = limpa (frontend manda "").
        if (email != null) { sets.add("email = ?"); args.add(email); }
        if (phone != null) { sets.add("phone = ?"); args.add(phone); }
        if (document != null) { sets.add("document = ?"); args.add(document); }
        if (birthDate != null) { sets.add("birth_date = ?"); args.add(Date.valueOf(birthDate)); }
        if (contactId != null) { sets.add("contact_id = ?"); args.add(contactId); }
        if (notes != null) { sets.add("notes = ?"); args.add(notes); }

        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update(
                "update dental_patients set " + String.join(", ", sets)
                    + " where company_id = ? and id = ?",
                args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    /** Hard delete. Lança DataIntegrityViolation se houver appointment referenciando (FK restrict). */
    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update(
            "delete from dental_patients where company_id = ? and id = ?", companyId, id) > 0;
    }
}
