package com.finance.portal.auth.presentation.mapper;

import com.finance.portal.auth.application.model.RegisterUserCommand;
import com.finance.portal.auth.presentation.dto.RegisterRequest;
import org.springframework.stereotype.Component;

@Component
public class AuthPresentationMapper {

    public RegisterUserCommand toCommand(RegisterRequest request) {
        return new RegisterUserCommand(
                request.getUsername(),
                request.getEmail(),
                request.getPassword(),
                request.getFirstName(),
                request.getLastName());
    }
}
