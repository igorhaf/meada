package com.meada.whatsapp.cms;

import com.fasterxml.jackson.databind.JsonNode;
import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.cms.CmsService.DomainTakenException;
import com.meada.whatsapp.cms.CmsService.InvalidBlocksException;
import com.meada.whatsapp.cms.CmsService.InvalidDomainException;
import com.meada.whatsapp.profiles.ProfileFeature;
import com.meada.whatsapp.profiles.features.ProfileFeatureGuard;
import com.meada.whatsapp.profiles.features.ProfileFeatureGuard.FeatureDisabledException;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * CMS do TENANT (SM-M). Edição da própria página: conteúdo (title + blocks ordenados), publicação e
 * domínio próprio. TODOS os endpoints estão atrás do gate {@link ProfileFeatureGuard#requireFeature}
 * — se o nicho do tenant NÃO tem a feature CMS ligada (camada 9.0), retorna 403 feature_disabled.
 */
@RestController
public class CmsTenantController {

    private final CmsService service;
    private final ProfileFeatureGuard featureGuard;

    public CmsTenantController(CmsService service, ProfileFeatureGuard featureGuard) {
        this.service = service;
        this.featureGuard = featureGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Resolve o companyId exigindo a feature CMS ligada; lança p/ o caller tratar 403. */
    private UUID gate(AuthenticatedUser user) {
        return featureGuard.requireFeature(user, ProfileFeature.CMS);
    }

    public record ContentRequest(String title, JsonNode blocks) {}
    public record PublishRequest(boolean published) {}
    public record DomainRequest(@Size(max = 253) String domain) {}

    @GetMapping("/api/cms/page")
    public ResponseEntity<Object> getPage(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = gate(user);
        } catch (FeatureDisabledException e) {
            return error(403, "Forbidden", "feature_disabled");
        }
        return ResponseEntity.ok(service.getOrCreate(companyId));
    }

    @PutMapping("/api/cms/page")
    public ResponseEntity<Object> saveContent(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestBody ContentRequest req) {
        UUID companyId;
        try {
            companyId = gate(user);
        } catch (FeatureDisabledException e) {
            return error(403, "Forbidden", "feature_disabled");
        }
        try {
            return ResponseEntity.ok(service.saveContent(companyId, req.title(), req.blocks()));
        } catch (InvalidBlocksException e) {
            return error(400, "Bad Request", "invalid_blocks");
        }
    }

    @PutMapping("/api/cms/page/publish")
    public ResponseEntity<Object> setPublished(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestBody PublishRequest req) {
        UUID companyId;
        try {
            companyId = gate(user);
        } catch (FeatureDisabledException e) {
            return error(403, "Forbidden", "feature_disabled");
        }
        return ResponseEntity.ok(service.setPublished(companyId, req.published()));
    }

    @PutMapping("/api/cms/page/domain")
    public ResponseEntity<Object> setDomain(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestBody DomainRequest req) {
        UUID companyId;
        try {
            companyId = gate(user);
        } catch (FeatureDisabledException e) {
            return error(403, "Forbidden", "feature_disabled");
        }
        try {
            return ResponseEntity.ok(service.setDomain(companyId, req.domain()));
        } catch (InvalidDomainException e) {
            return error(400, "Bad Request", "invalid_domain");
        } catch (DomainTakenException e) {
            return error(409, "Conflict", "domain_taken");
        }
    }
}
