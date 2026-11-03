package co.edu.ufpso.helpdesk.empresas.domain;

import org.springframework.data.mongodb.core.mapping.Document;

import co.edu.ufpso.helpdesk.common.dominio.DocumentoBase;

/**
 * Documento de la colección {@code empresas} (HU01 a HU04). Modelo 1.2, sección 1.
 *
 * <p>La empresa no guarda plan ni estado: eso vive en {@code membresias}. Una empresa recién
 * creada queda sin soporte hasta que se le asigne una membresía (HU01).
 *
 * <p>{@code nit} es único ({@code ux_empresas_nit}) y tiene el formato {@code 900123456-1}.
 */
@Document("empresas")
public class Empresa extends DocumentoBase {

    private String nit;
    private String razonSocial;
    private Contacto contacto;

    public Empresa() {
    }

    public String getNit() {
        return nit;
    }

    public void setNit(String nit) {
        this.nit = nit;
    }

    public String getRazonSocial() {
        return razonSocial;
    }

    public void setRazonSocial(String razonSocial) {
        this.razonSocial = razonSocial;
    }

    public Contacto getContacto() {
        return contacto;
    }

    public void setContacto(Contacto contacto) {
        this.contacto = contacto;
    }
}
