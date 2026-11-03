package co.edu.ufpso.helpdesk.tickets.service;

import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import co.edu.ufpso.helpdesk.tickets.repository.TicketRepository;

/** Cuántos tickets tiene una empresa (HU04). Sin dependencias de otros módulos. */
@Service
public class TicketsPorEmpresaService {

    private final TicketRepository repositorio;

    public TicketsPorEmpresaService(TicketRepository repositorio) {
        this.repositorio = repositorio;
    }

    public long contar(ObjectId empresaId) {
        return repositorio.countByEmpresaId(empresaId);
    }
}
