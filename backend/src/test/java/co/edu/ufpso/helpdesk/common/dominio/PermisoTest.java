package co.edu.ufpso.helpdesk.common.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/** Grupo y descripción del catálogo (acta 2.4). Que los códigos coincidan con 02_roles.js lo prueba RolPersistenciaTest. */
class PermisoTest {

    @Test
    void sonLos49PermisosYTodosTienenGrupoYDescripcion() {
        assertThat(Permiso.values()).hasSize(49);
        for (Permiso p : Permiso.values()) {
            assertThat(p.getGrupo()).as(p.name()).isNotBlank();
            assertThat(p.getDescripcion()).as(p.name()).isNotBlank();
        }
    }

    @Test
    void ningunaDescripcionSeRepite() {
        Set<String> descripciones = Arrays.stream(Permiso.values()).map(Permiso::getDescripcion)
                .collect(Collectors.toSet());
        assertThat(descripciones).hasSize(Permiso.values().length);
    }

    @Test
    void losPermisosDeAutoservicioNoSeExigenAlAsignar() {
        // Permisos por defecto de CLIENTE_ADMIN (semilla) frente al rol CLIENTE.
        Set<Permiso> clienteAdmin = Set.of(Permiso.TICKETS_CREAR, Permiso.TICKETS_VER, Permiso.TICKETS_EDITAR_PROPIOS,
                Permiso.ARTICULOS_VER, Permiso.CAPACITACIONES_VER, Permiso.USUARIOS_CREAR);
        Set<Permiso> cliente = Set.of(Permiso.TICKETS_CREAR, Permiso.TICKETS_VER_PROPIOS,
                Permiso.TICKETS_EDITAR_PROPIOS, Permiso.ARTICULOS_VER, Permiso.CAPACITACIONES_VER,
                Permiso.INSCRIPCIONES_PROPIAS);
        assertThat(Permiso.faltantesParaAsignar(cliente, clienteAdmin)).isEmpty();

        // Un permiso que no es de autoservicio sí se exige.
        assertThat(Permiso.faltantesParaAsignar(Set.of(Permiso.TICKETS_ELIMINAR, Permiso.TICKETS_VER_PROPIOS),
                clienteAdmin)).containsExactly(Permiso.TICKETS_ELIMINAR);
        assertThat(Permiso.AUTOSERVICIO).containsExactlyInAnyOrder(Permiso.TICKETS_VER_PROPIOS,
                Permiso.TICKETS_EDITAR_PROPIOS, Permiso.INSCRIPCIONES_PROPIAS);
    }
}
