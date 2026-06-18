package com.meada.whatsapp.cms;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Catálogo HARDCODED de tipos de bloco do CMS (SM-M, page builder). Cada bloco de uma página é
 * {@code {id, type, props}}; {@code type} é um destes. As props variam por tipo (validadas
 * app-level no {@code CmsService}; o JSONB não tem CHECK pra deixar os blocos evoluírem).
 *
 * <p>Espelhado 1:1 por {@code frontend/lib/cms/cms-block-type.ts} ({@code CmsBlockTypeParityTest}
 * garante a paridade). Adicionar um tipo = editar os 2 arquivos + o editor/render do frontend +
 * rodar a paridade. Tipos iniciais:
 * <ul>
 *   <li>{@code hero} — título + subtítulo + botão (label/href).</li>
 *   <li>{@code text} — conteúdo livre em markdown.</li>
 *   <li>{@code services} — título + lista de itens (name, description, price).</li>
 *   <li>{@code contact} — telefone/WhatsApp, endereço, horário + botão.</li>
 * </ul>
 */
public enum CmsBlockType {
    HERO("hero"),
    TEXT("text"),
    SERVICES("services"),
    CONTACT("contact"),
    GALLERY("gallery"),
    FAQ("faq"),
    TESTIMONIALS("testimonials"),
    MAP("map");

    private final String id;

    CmsBlockType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<CmsBlockType> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(t -> t.id.equals(id)).findFirst();
    }

    public static List<CmsBlockType> allActive() {
        return List.of(values());
    }
}
