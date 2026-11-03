package co.edu.ufpso.helpdesk.usuarios.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import co.edu.ufpso.helpdesk.common.dominio.DocumentoBase;

/**
 * Documento de la colección {@code usuarios} (HU05 a HU08). Modelo 1.2, sección 3.
 *
 * <ul>
 *   <li>{@code email} se guarda en minúsculas: el validador rechaza mayúsculas y el índice único
 *       distingue entre ellas. El setter lo normaliza.</li>
 *   <li>{@code passwordHash} es BCrypt de 60 caracteres. Nunca sale en una respuesta (DTO).</li>
 *   <li>{@code rolIds}: de 1 a 10, sin repetidos, todos del mismo alcance (eso último lo revisa
 *       el servicio, porque exige consultar {@code roles}).</li>
 *   <li>{@code empresaId}: null para usuarios de roles GLOBAL. Es obligatorio aunque valga null,
 *       por eso lleva {@code @Field(write = ALWAYS)}.</li>
 * </ul>
 */
@Document("usuarios")
public class Usuario extends DocumentoBase {

    private String nombre;
    private String email;
    private String passwordHash;
    private List<ObjectId> rolIds = new ArrayList<>();

    /** null = usuario de Sistemas Integrados (roles GLOBAL). */
    @Field(write = Field.Write.ALWAYS)
    private ObjectId empresaId;

    private Boolean activo;

    public Usuario() {
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getEmail() {
        return email;
    }

    /** Guarda el email sin espacios a los lados y en minúsculas. */
    public void setEmail(String email) {
        this.email = (email == null) ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public List<ObjectId> getRolIds() {
        return rolIds;
    }

    public void setRolIds(List<ObjectId> rolIds) {
        this.rolIds = rolIds;
    }

    public ObjectId getEmpresaId() {
        return empresaId;
    }

    public void setEmpresaId(ObjectId empresaId) {
        this.empresaId = empresaId;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }
}
