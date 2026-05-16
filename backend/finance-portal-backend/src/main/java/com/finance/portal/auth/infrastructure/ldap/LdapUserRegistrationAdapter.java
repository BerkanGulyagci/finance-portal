package com.finance.portal.auth.infrastructure.ldap;

import com.finance.portal.auth.application.model.RegisterUserCommand;
import com.finance.portal.auth.application.port.UserRegistrationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;

/**
 * Registers users directly in ApacheDS (LDAP).
 * Keycloak has syncRegistrations=true, so it will automatically pick up new LDAP users.
 * Password is stored in LDAP, so Keycloak password flow works correctly.
 */
@Component
public class LdapUserRegistrationAdapter implements UserRegistrationPort {

    private static final Logger log = LoggerFactory.getLogger(LdapUserRegistrationAdapter.class);

    @Value("${ldap.url:ldap://localhost:10389}")
    private String ldapUrl;

    @Value("${ldap.base-dn:dc=openmicroscopy,dc=org}")
    private String baseDn;

    @Value("${ldap.admin-dn:uid=admin,ou=system}")
    private String adminDn;

    @Value("${ldap.admin-password:secret}")
    private String adminPassword;

    @Override
    public void register(RegisterUserCommand command) {
        String userDn = "uid=" + command.getUsername() + "," + baseDn;

        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, ldapUrl);
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, adminDn);
        env.put(Context.SECURITY_CREDENTIALS, adminPassword);

        DirContext ctx = null;
        try {
            ctx = new InitialDirContext(env);

            try {
                ctx.lookup(userDn);
                throw new IllegalArgumentException("Bu kullanıcı adı zaten kullanılıyor.");
            } catch (NamingException e) {
                if (e instanceof javax.naming.NameAlreadyBoundException) {
                    throw new IllegalArgumentException("Bu kullanıcı adı zaten kullanılıyor.");
                }
            }

            Attributes attrs = new BasicAttributes(true);

            Attribute objClass = new BasicAttribute("objectClass");
            objClass.add("inetOrgPerson");
            objClass.add("organizationalPerson");
            objClass.add("person");
            objClass.add("top");
            attrs.put(objClass);

            attrs.put("uid", command.getUsername());
            attrs.put("cn", command.getFirstName() + " " + command.getLastName());
            attrs.put("sn", command.getLastName());
            attrs.put("givenName", command.getFirstName());
            attrs.put("mail", command.getEmail());
            attrs.put("userPassword", command.getPassword());

            ctx.createSubcontext(userDn, attrs);
            log.info("User '{}' created in LDAP successfully", command.getUsername());

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (NamingException e) {
            log.error("LDAP error while registering user '{}': {}", command.getUsername(), e.getMessage());
            throw new RuntimeException("Kayıt işlemi başarısız oldu. Lütfen tekrar deneyin.");
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException ignored) {
                }
            }
        }
    }
}
