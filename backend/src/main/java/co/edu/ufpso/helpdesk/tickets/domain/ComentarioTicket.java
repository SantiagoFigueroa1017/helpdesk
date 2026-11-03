package co.edu.ufpso.helpdesk.tickets.domain;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;

import co.edu.ufpso.helpdesk.common.dominio.DocumentoBase;

/**
 * Documento de la colección {@code comentariosTicket} (CRUD fuera de las 40). Modelo 1.2,
 * sección 8.
 *
 * <p>Colección propia porque un ticket puede acumular cientos de comentarios. Solo el autor edita
 * el suyo (permiso {@code COMENTARIOS_CREAR}); al editarlo, {@code editado} pasa a true. Se borran
 * en la transacción de HU24.
 */
@Document("comentariosTicket")
public class ComentarioTicket extends DocumentoBase {

    private ObjectId ticketId;
    private ObjectId autorId;
    private String texto;
    private Boolean editado = false;

    public ComentarioTicket() {
    }

    public ObjectId getTicketId() {
        return ticketId;
    }

    public void setTicketId(ObjectId ticketId) {
        this.ticketId = ticketId;
    }

    public ObjectId getAutorId() {
        return autorId;
    }

    public void setAutorId(ObjectId autorId) {
        this.autorId = autorId;
    }

    public String getTexto() {
        return texto;
    }

    public void setTexto(String texto) {
        this.texto = texto;
    }

    public Boolean getEditado() {
        return editado;
    }

    public void setEditado(Boolean editado) {
        this.editado = editado;
    }
}
