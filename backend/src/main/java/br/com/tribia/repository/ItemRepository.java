package br.com.tribia.repository;

import br.com.tribia.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemRepository extends JpaRepository<Item, Long> {

    /** Itens das notas do cliente, com a nota já carregada. Competências no formato AAAA-MM; null não filtra. */
    @Query("""
            select i from Item i join fetch i.nota n
            where n.cliente.id = :clienteId
              and (:de is null or n.competencia >= :de)
              and (:ate is null or n.competencia <= :ate)
            order by n.dataEmissao, n.id, i.nItem
            """)
    List<Item> doClienteNoPeriodo(@Param("clienteId") Long clienteId, @Param("de") String de, @Param("ate") String ate);

    @Query("select i from Item i join fetch i.nota n join fetch n.cliente where n.id = :notaId order by i.nItem")
    List<Item> daNota(@Param("notaId") Long notaId);
}
