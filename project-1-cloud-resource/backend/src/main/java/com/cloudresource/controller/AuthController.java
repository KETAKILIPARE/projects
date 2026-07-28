package com.cloudresource.controller;

import com.cloudresource.domain.User;
import com.cloudresource.domain.UserRole;
import com.cloudresource.dto.LoginRequest;
import com.cloudresource.dto.LoginResponse;
import com.cloudresource.dto.RegisterRequest;
import com.cloudresource.repository.UserRepository;
import com.cloudresource.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.findByUsername(request.username()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        UserRole role = request.role() != null ? UserRole.valueOf(request.role()) : UserRole.OPERATOR;
        userRepository.save(new User(request.username(), passwordEncoder.encode(request.password()), role));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );
        User user = userRepository.findByUsername(auth.getName()).orElseThrow();
        String token = jwtUtil.generateToken(auth.getName(), user.getRole().name());
        return ResponseEntity.ok(new LoginResponse(token));
    }
}
