package com.tasksphere.iam.controller;

import com.tasksphere.iam.adapter.in.AdminController;
import com.tasksphere.iam.domain.UserEntity;
import com.tasksphere.iam.port.out.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ═══════════════════════════════════════════════════════════════════
 * TESTS UNITAIRES : AdminController
 * ═══════════════════════════════════════════════════════════════════
 *
 * PHASE 3 — FEATURE 5 : Tests du contrôleur d'administration
 * ────────────────────────────────────────────────────────────
 *
 * PRINCIPE — TESTS UNITAIRES avec MOCKS :
 * ──────────────────────────────────────────
 * On mock les dépendances (UserRepository, ApplicationEventPublisher)
 * pour tester UNIQUEMENT la logique du contrôleur, sans base de données.
 *
 * PATTERN AAA (Arrange-Act-Assert) :
 * 1. Arrange : préparer les mocks et les données de test
 * 2. Act : appeler la méthode du contrôleur
 * 3. Assert : vérifier le résultat et les interactions avec les mocks
 *
 * NOTE TECHNIQUE — doReturn().when() vs when().thenReturn() :
 * ──────────────────────────────────────────────────────────────
 * Authentication.getAuthorities() retourne Collection<? extends GrantedAuthority>.
 * Le wildcard (?) rend when().thenReturn() impossible car Java ne peut pas
 * convertir Collection<GrantedAuthority> en Collection<? extends GrantedAuthority>
 * (invariance des generics). doReturn().when() contourne ce problème car
 * il utilise les types bruts (raw types) en interne.
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private AdminController adminController;

    private UserEntity testUser;

    @BeforeEach
    void setUp() {
        testUser = new UserEntity();
        testUser.setId("user-123");
        testUser.setUsername("testuser");
        testUser.setEmail("test@tasksphere.com");
        testUser.setRole("USER");
        testUser.setEnabled(true);
    }

    // ═══════════════════════════════════════════════════════
    // GET /api/v1/iam/admin/users
    // ═══════════════════════════════════════════════════════

    @Test
    @DisplayName("getAllUsers — ADMIN peut lister les utilisateurs")
    void getAllUsers_adminRole_returnsUsers() {
        // Arrange
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .when(authentication).getAuthorities();
        when(authentication.getName()).thenReturn("admin@tasksphere.com");
        when(userRepository.findAll()).thenReturn(List.of(testUser));

        // Act
        ResponseEntity<?> response = adminController.getAllUsers(authentication);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userRepository).findAll();
    }

    @Test
    @DisplayName("getAllUsers — MANAGER peut lister les utilisateurs (lecture seule)")
    void getAllUsers_managerRole_returnsUsers() {
        // Arrange
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_MANAGER")))
                .when(authentication).getAuthorities();
        when(authentication.getName()).thenReturn("manager@tasksphere.com");
        when(userRepository.findAll()).thenReturn(List.of(testUser));

        // Act
        ResponseEntity<?> response = adminController.getAllUsers(authentication);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("getAllUsers — USER reçoit 403 Forbidden")
    void getAllUsers_userRole_returnsForbidden() {
        // Arrange
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .when(authentication).getAuthorities();
        when(authentication.getName()).thenReturn("user@tasksphere.com");

        // Act
        ResponseEntity<?> response = adminController.getAllUsers(authentication);

        // Assert
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(userRepository, never()).findAll();
    }

    // ═══════════════════════════════════════════════════════
    // PATCH /api/v1/iam/admin/users/{id}/role
    // ═══════════════════════════════════════════════════════

    @Test
    @DisplayName("updateUserRole — ADMIN peut changer le rôle")
    void updateUserRole_adminRole_updatesRole() {
        // Arrange
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .when(authentication).getAuthorities();
        when(authentication.getName()).thenReturn("admin@tasksphere.com");
        when(userRepository.findById("user-123")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(UserEntity.class))).thenReturn(testUser);

        var request = new com.tasksphere.iam.dto.RoleUpdateRequest("MANAGER");

        // Act
        ResponseEntity<?> response = adminController.updateUserRole("user-123", request, authentication);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userRepository).save(any(UserEntity.class));
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    @DisplayName("updateUserRole — MANAGER reçoit 403 Forbidden")
    void updateUserRole_managerRole_returnsForbidden() {
        // Arrange
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_MANAGER")))
                .when(authentication).getAuthorities();

        var request = new com.tasksphere.iam.dto.RoleUpdateRequest("ADMIN");

        // Act
        ResponseEntity<?> response = adminController.updateUserRole("user-123", request, authentication);

        // Assert
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateUserRole — Utilisateur inexistant retourne 404")
    void updateUserRole_nonExistentUser_returnsNotFound() {
        // Arrange
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .when(authentication).getAuthorities();
        when(userRepository.findById("nonexistent")).thenReturn(Optional.empty());

        var request = new com.tasksphere.iam.dto.RoleUpdateRequest("MANAGER");

        // Act
        ResponseEntity<?> response = adminController.updateUserRole("nonexistent", request, authentication);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // ═══════════════════════════════════════════════════════
    // PATCH /api/v1/iam/admin/users/{id}/toggle
    // ═══════════════════════════════════════════════════════

    @Test
    @DisplayName("toggleUserStatus — ADMIN peut désactiver un utilisateur")
    void toggleUserStatus_adminRole_togglesStatus() {
        // Arrange
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .when(authentication).getAuthorities();
        when(authentication.getName()).thenReturn("admin@tasksphere.com");
        when(userRepository.findById("user-123")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(UserEntity.class))).thenReturn(testUser);

        // Act
        ResponseEntity<?> response = adminController.toggleUserStatus("user-123", authentication);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userRepository).save(any(UserEntity.class));
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    @DisplayName("toggleUserStatus — Utilisateur inexistant retourne 404")
    void toggleUserStatus_nonExistentUser_returnsNotFound() {
        // Arrange
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .when(authentication).getAuthorities();
        when(userRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // Act
        ResponseEntity<?> response = adminController.toggleUserStatus("nonexistent", authentication);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}