package co.edu.ufpso.helpdesk.conocimiento.repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import co.edu.ufpso.helpdesk.conocimiento.domain.Articulo;

/**
 * Acceso a {@code articulos}. Solo lo usa el módulo de conocimiento (regla entre módulos de 2.2).
 * Las consultas con índice propio viven en los servicios, con {@code MongoTemplate}: el listado
 * ({@code ix_articulos_modulo_fecha}), las dos búsquedas ({@code txt_articulos} y
 * {@code vec_articulos_embedding}, solo Atlas) y el {@code $pull} de HU24 ({@code ix_articulos_ticketIds}).
 */
public interface ArticuloRepository extends MongoRepository<Articulo, ObjectId> {
}
