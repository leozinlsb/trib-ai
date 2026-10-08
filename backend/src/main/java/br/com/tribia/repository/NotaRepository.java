package br.com.tribia.repository;

import br.com.tribia.model.Nota;
import br.com.tribia.model.TipoNota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotaRepository extends JpaRepository<Nota, Long> {

    boolean existsByClienteIdAndChave(Long clienteId, String chave);

    /** Filtros opcionais: parâmetro null não filtra. */
    @Query("""
            select n from Nota n
            where n.cliente.id = :clienteId
              and (:tipo is null or n.tipo = :tipo)
              and (:competencia is null or n.competencia = :competencia)
            order by n.dataEmissao, n.id
            """)
    List<Nota> buscar(@Param("clienteId") Long clienteId,
                      @Param("tipo") TipoNota tipo,
                      @Param("competencia") String competencia);

    @Query("select n from Nota n left join fetch n.itens where n.id = :id")
    Optional<Nota> buscarComItens(@Param("id") Long id);

    boolean existsByClienteId(Long clienteId);

    /** Notas do cliente com itens, entre duas competências (AAAA-MM, inclusive). Para o relatório. */
    @Query("""
            select distinct n from Nota n left join fetch n.itens
            where n.cliente.id = :clienteId
              and (:de is null or n.competencia >= :de)
              and (:ate is null or n.competencia <= :ate)
            order by n.dataEmissao, n.id
            """)
    List<Nota> buscarComItensNoPeriodo(@Param("clienteId") Long clienteId,
                                       @Param("de") String de,
                                       @Param("ate") String ate);
}
