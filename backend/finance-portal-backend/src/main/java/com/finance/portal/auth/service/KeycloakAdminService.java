package com.finance.portal.auth.service;

import com.finance.portal.auth.dto.RegisterRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.*;
import java.util.Hashtable;

/**
 * Registers users directly in ApacheDS (LDAP).
 * Keycloak has syncRegistrations=true, so it will automatically pick up new LDAP users.
 * Password is stored in LDAP, so Keycloak password flow works correctly.
 */
@Service
public class KeycloakAdminService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminService.class);

    @Value("${ldap.url:ldap://localhost:10389}")
    private String ldapUrl;

    @Value("${ldap.base-dn:dc=openmicroscopy,dc=org}")
    private String baseDn;

    @Value("${ldap.admin-dn:uid=admin,ou=system}")
    private String adminDn;

    @Value("${ldap.admin-password:secret}")
    private String adminPassword;

    public void registerUser(RegisterRequest req) {
        String userDn = "uid=" + req.getUsername() + "," + baseDn;

        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, ldapUrl);
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, adminDn);
        env.put(Context.SECURITY_CREDENTIALS, adminPassword);

        DirContext ctx = null;
        try {
            ctx = new InitialDirContext(env);

            // Check if user already exists
            try {
                ctx.lookup(userDn);
                throw new IllegalArgumentException("Bu kullanıcı adı zaten kullanılıyor.");
            } catch (NamingException e) {
                // User doesn't exist, proceed
                if (e instanceof javax.naming.NameAlreadyBoundException) {
                    throw new IllegalArgumentException("Bu kullanıcı adı zaten kullanılıyor.");
                }
            }

            // Build LDAP attributes
            Attributes attrs = new BasicAttributes(true);

            // objectClass
            Attribute objClass = new BasicAttribute("objectClass");
            objClass.add("inetOrgPerson");
            objClass.add("organizationalPerson");
            objClass.add("person");
            objClass.add("top");
            attrs.put(objClass);

            attrs.put("uid", req.getUsername());
            attrs.put("cn", req.getFirstName() + " " + req.getLastName());
            attrs.put("sn", req.getLastName());
            attrs.put("givenName", req.getFirstName());
            attrs.put("mail", req.getEmail());
            attrs.put("userPassword", req.getPassword());

            ctx.createSubcontext(userDn, attrs);
            log.info("User '{}' created in LDAP successfully", req.getUsername());

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (NamingException e) {
            log.error("LDAP error while registering user '{}': {}", req.getUsername(), e.getMessage());
            throw new RuntimeException("Kayıt işlemi başarısız oldu. Lütfen tekrar deneyin.");
        } finally {
            if (ctx != null) {
                try { ctx.close(); } catch (NamingException ignored) {}
            }
        }
    }
}
