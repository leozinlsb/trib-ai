package br.com.tribia.repository;

import br.com.tribia.model.AnaliseFiscal;
import br.com.tribia.model.StatusAnalise;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface AnaliseFiscalRepository extends JpaRepository<AnaliseFiscal, Long> {

    /** Filtros opcionais (null não filtra). termo já em minúsculas e com %; ate é exclusivo. */
    @Query("""
            select a from AnaliseFiscal a
            where a.cliente.id = :clienteId
              and (:status is null or a.status = :status)
              and (:termo is null or lower(a.mercadoria) like :termo or a.ncmSugerida like :termo)
              and (:de is null or a.criadaEm >= :de)
              and (:ate is null or a.criadaEm < :ate)
            """)
    Page<AnaliseFiscal> buscar(@Param("clienteId") Long clienteId, @Param("status") StatusAnalise status,
                               @Param("termo") String termo, @Param("de") Instant de, @Param("ate") Instant ate,
                               Pageable pagina);

    @Query("select a.status, count(a) from AnaliseFiscal a where a.cliente.id = :clienteId group by a.status")
    List<Object[]> contarPorStatus(@Param("clienteId") Long clienteId);

    /** Análises que estavam em andamento quando o servidor parou (o processamento roda em memória). */
    @Modifying
    @Query("""
            update AnaliseFiscal a set a.status = br.com.tribia.model.StatusAnalise.FALHA, a.mensagem = :mensagem,
                   a.atualizadaEm = :agora
            where a.status in :emAndamento
            """)
    int interromperEmAndamento(@Param("emAndamento") Collection<StatusAnalise> emAndamento,
                               @Param("mensagem") String mensagem, @Param("agora") Instant agora);
}
