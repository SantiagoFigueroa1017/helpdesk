package co.edu.ufpso.helpdesk.roles.domain;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.mongodb.core.mapping.Document;

import co.edu.ufpso.helpdesk.common.dominio.AlcanceRol;
import co.edu.ufpso.helpdesk.common.dominio.DocumentoBase;
import co.edu.ufpso.helpdesk.common.dominio.Permiso;

/**
 * Documento de la colección {@code roles} (HU25 a HU28). Modelo 1.2, sección 2.
 *
 * <p>{@code permisos} va embebido: tiene tope fijo (el catálogo) y se lee con el rol en cada
 * autorización. El validador exige al menos uno y sin repetidos; la unicidad la revisa también
 * el servicio para responder 422 y no 500.
 *
 * <p>La cantidad de usuarios por rol (HU26) no se guarda: se cuenta en {@code usuarios.rolIds}.
 * Los roles con {@code esSistema: true} no se eliminan ni cambian nombre o alcance (servicio).
 */
@Document("roles")
public class Rol extends DocumentoBase {

    private String nombre;
    private String descripcion;
    private AlcanceRol alcance;
    private List<Permiso> permisos = new ArrayList<>();
    private Boolean activo;
    private Boolean esSistema;

    public Rol() {
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public AlcanceRol getAlcance() {
        return alcance;
    }

    public void setAlcance(AlcanceRol alcance) {
        this.alcance = alcance;
    }

    public List<Permiso> getPermisos() {
        return permisos;
    }

    public void setPermisos(List<Permiso> permisos) {
        this.permisos = permisos;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }

    public Boolean getEsSistema() {
        return esSistema;
    }

    public void setEsSistema(Boolean esSistema) {
        this.esSistema = esSistema;
    }
}
