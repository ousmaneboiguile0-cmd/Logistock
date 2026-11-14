package com.logistock.security;

/** Utilisateur authentifié, disponible dans les contrôleurs via @AuthenticationPrincipal. */
public record AuthUser(int id, String email, String role) {
}
