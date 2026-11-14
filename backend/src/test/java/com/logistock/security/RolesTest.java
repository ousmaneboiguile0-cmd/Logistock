package com.logistock.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RolesTest {

    @Test
    void chaqueRoleDuCahierDesChargesALeBonneAutorite() {
        assertEquals("ROLE_ADMIN", Roles.authority("Administrateur"));
        assertEquals("ROLE_GESTIONNAIRE", Roles.authority("Gestionnaire de stock"));
        assertEquals("ROLE_COMMERCIAL", Roles.authority("Commercial"));
    }

    @Test
    void unRoleInconnuNeDonneAucunDroit() {
        assertEquals("ROLE_INCONNU", Roles.authority("Consultant"));
        assertEquals("ROLE_INCONNU", Roles.authority(null));
    }
}
