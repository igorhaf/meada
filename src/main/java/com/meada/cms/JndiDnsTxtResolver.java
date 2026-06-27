package com.meada.cms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

/**
 * Impl de produção do {@link DnsTxtResolver}: consulta TXT via JNDI DNS (embutido no JDK, sem libs).
 * A verificação de posse de domínio (SM-N) lê {@code TXT} do host pra encontrar o token
 * {@code _meada-verify=<token>}. Falha de rede/resolução → lista vazia (verificação simplesmente não
 * passa; nunca quebra o fluxo).
 */
@Component
public class JndiDnsTxtResolver implements DnsTxtResolver {

    private static final Logger log = LoggerFactory.getLogger(JndiDnsTxtResolver.class);

    @Override
    public List<String> txtRecords(String host) {
        List<String> out = new ArrayList<>();
        if (host == null || host.isBlank()) {
            return out;
        }
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            // timeout 3s, 1 retry — não pendurar o request de verificação.
            env.put("com.sun.jndi.dns.timeout.initial", "3000");
            env.put("com.sun.jndi.dns.timeout.retries", "1");
            InitialDirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(host, new String[] {"TXT"});
            Attribute txt = attrs.get("TXT");
            if (txt != null) {
                Enumeration<?> vals = txt.getAll();
                while (vals.hasMoreElements()) {
                    // valores TXT vêm entre aspas; normaliza removendo-as.
                    out.add(vals.nextElement().toString().replace("\"", "").trim());
                }
            }
            ctx.close();
        } catch (Exception e) {
            log.debug("DNS TXT lookup falhou p/ {} ({}) — verificação não passa", host, e.getMessage());
        }
        return out;
    }
}
