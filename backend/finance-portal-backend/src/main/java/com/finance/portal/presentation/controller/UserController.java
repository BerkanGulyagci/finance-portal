package com.finance.portal.presentation.controller;

import com.finance.portal.application.service.UserService;
import com.finance.portal.presentation.dto.ApiResponse;
import com.finance.portal.presentation.dto.CreateUserRequest;
import com.finance.portal.presentation.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        
        UserResponse user = userService.createUser(request);
        ApiResponse<UserResponse> response = ApiResponse.success(
            user, 
            "User created successfully"
        );
        
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}