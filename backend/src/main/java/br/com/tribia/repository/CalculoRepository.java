package br.com.tribia.repository;

import br.com.tribia.model.Calculo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CalculoRepository extends JpaRepository<Calculo, Long> {

    List<Calculo> findByItemIdIn(Collection<Long> itemIds);

    /** Sem clearAutomatically: o CalculoService continua usando a nota e o cliente já carregados na transação. */
    @Modifying(flushAutomatically = true)
    @Query("delete from Calculo c where c.item.id in :itemIds")
    void apagarDosItens(@Param("itemIds") Collection<Long> itemIds);
}
