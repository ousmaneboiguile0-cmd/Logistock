package com.logistock.security;

/** Les trois rôles du cahier des charges et leur correspondance avec les autorités Spring Security. */
public final class Roles {

    public static final String ADMIN = "Administrateur";
    public static final String GESTIONNAIRE = "Gestionnaire de stock";
    public static final String COMMERCIAL = "Commercial";

    private Roles() {
    }

    /** Transforme le libellé du rôle (table role) en autorité utilisable par hasRole(...). */
    public static String authority(String libelle) {
        if (ADMIN.equals(libelle)) {
            return "ROLE_ADMIN";
        }
        if (GESTIONNAIRE.equals(libelle)) {
            return "ROLE_GESTIONNAIRE";
        }
        if (COMMERCIAL.equals(libelle)) {
            return "ROLE_COMMERCIAL";
        }
        return "ROLE_INCONNU";
    }
}
