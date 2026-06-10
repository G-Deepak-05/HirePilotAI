package com.hirepilot.controller;

import com.hirepilot.domain.User;
import com.hirepilot.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    /**
     * POST /api/users
     * Creates the local user profile (single-user mode).
     */
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            return ResponseEntity.ok(
                    UserResponse.from(userRepository.findByEmail(request.email()).orElseThrow()));
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .targetRoles(request.targetRoles())
                .targetLocations(request.targetLocations())
                .minimumSalary(request.minimumSalary())
                .build();

        return ResponseEntity.ok(UserResponse.from(userRepository.save(user)));
    }

    /**
     * GET /api/users/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID id) {
        return userRepository.findById(id)
                .map(u -> ResponseEntity.ok(UserResponse.from(u)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * PUT /api/users/{id}/preferences
     */
    @PutMapping("/{id}/preferences")
    public ResponseEntity<UserResponse> updatePreferences(
            @PathVariable UUID id,
            @RequestBody UpdatePreferencesRequest request) {

        return userRepository.findById(id)
                .map(user -> {
                    if (request.targetRoles() != null)     user.setTargetRoles(request.targetRoles());
                    if (request.targetLocations() != null) user.setTargetLocations(request.targetLocations());
                    if (request.minimumSalary() != null)   user.setMinimumSalary(request.minimumSalary());
                    return ResponseEntity.ok(UserResponse.from(userRepository.save(user)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ─── DTOs ─────────────────────────────────────────────────────────────────

    public record CreateUserRequest(
            @NotBlank String name,
            @NotBlank @Email String email,
            String[] targetRoles,
            String[] targetLocations,
            Integer minimumSalary
    ) {}

    public record UpdatePreferencesRequest(
            String[] targetRoles,
            String[] targetLocations,
            Integer minimumSalary
    ) {}

    public record UserResponse(
            UUID id, String name, String email,
            String[] targetRoles, String[] targetLocations,
            Integer minimumSalary, String createdAt
    ) {
        static UserResponse from(User u) {
            return new UserResponse(
                    u.getId(), u.getName(), u.getEmail(),
                    u.getTargetRoles(), u.getTargetLocations(),
                    u.getMinimumSalary(),
                    u.getCreatedAt() != null ? u.getCreatedAt().toString() : null
            );
        }
    }
}
