package co.edu.ufpso.helpdesk.usuarios.service;

import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import co.edu.ufpso.helpdesk.usuarios.repository.UsuarioRepository;

/**
 * Cuántos usuarios tiene una empresa (HU04). Sin dependencias de otros módulos, para que el
 * servicio de empresas lo use sin crear dependencias circulares.
 */
@Service
public class UsuariosPorEmpresaService {

    private final UsuarioRepository repositorio;

    public UsuariosPorEmpresaService(UsuarioRepository repositorio) {
        this.repositorio = repositorio;
    }

    public long contar(ObjectId empresaId) {
        return repositorio.countByEmpresaId(empresaId);
    }
}
