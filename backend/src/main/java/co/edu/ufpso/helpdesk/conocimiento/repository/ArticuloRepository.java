package co.edu.ufpso.helpdesk.conocimiento.repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import co.edu.ufpso.helpdesk.conocimiento.domain.Articulo;

/**
 * Acceso a {@code articulos}. Solo lo usa el servicio de conocimiento (regla entre módulos de 2.2).
 * La búsqueda por texto ({@code txt_articulos}), la vectorial ({@code vec_articulos_embedding},
 * solo Atlas) y el {@code $pull} de HU24 ({@code ix_articulos_ticketIds}) se agregan en 2.6 y 2.7.
 */
public interface ArticuloRepository extends MongoRepository<Articulo, ObjectId> {
}
