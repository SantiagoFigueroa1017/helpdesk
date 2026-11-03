package co.edu.ufpso.helpdesk.usuarios.dto;

/** Constantes compartidas por los DTO de usuarios. */
public final class UsuarioDtos {

    /**
     * Mismo formato que el validador de {@code usuarios.email}, pero sin exigir minúsculas ni
     * prohibir espacios a los lados: el documento los quita y pasa a minúsculas al guardar.
     */
    public static final String PATRON_EMAIL = "^\\s*[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\s*$";

    private UsuarioDtos() {
    }
}
