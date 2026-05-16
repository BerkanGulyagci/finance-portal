package com.finance.portal.auth.application.model;

/**
 * Kullanıcı kaydı use-case girdisi (presentation'dan bağımsız).
 */
public class RegisterUserCommand {

    private final String username;
    private final String email;
    private final String password;
    private final String firstName;
    private final String lastName;

    public RegisterUserCommand(String username, String email, String password,
                               String firstName, String lastName) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }
}
