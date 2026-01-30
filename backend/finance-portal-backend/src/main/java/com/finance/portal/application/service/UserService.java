package com.finance.portal.application.service;

import com.finance.portal.presentation.dto.CreateUserRequest;
import com.finance.portal.presentation.dto.UserResponse;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class UserService {

    private final AtomicLong idGenerator = new AtomicLong(1);

    public UserResponse createUser(CreateUserRequest request) {
        // Simulated user creation - in real app this would interact with repository
        Long id = idGenerator.getAndIncrement();
        return new UserResponse(id, request.getName(), request.getEmail());
    }
}