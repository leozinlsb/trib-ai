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

    /**
     * Reserva a análise para processamento: só passa de AGUARDANDO para INTERPRETANDO uma vez (1 = reservada,
     * 0 = outra execução já pegou ou ela já terminou). Evita processamento duplicado.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update AnaliseFiscal a set a.status = br.com.tribia.model.StatusAnalise.INTERPRETANDO,
                   a.tentativas = coalesce(a.tentativas, 0) + 1, a.atualizadaEm = :agora
            where a.id = :id and a.status = br.com.tribia.model.StatusAnalise.AGUARDANDO
            """)
    int reservar(@Param("id") Long id, @Param("agora") Instant agora);

    /** Análises que estavam em andamento quando o servidor parou, para retomar ou encerrar na inicialização. */
    @Query("select a.id from AnaliseFiscal a where a.status in :emAndamento order by a.criadaEm, a.id")
    List<Long> idsEmAndamento(@Param("emAndamento") Collection<StatusAnalise> emAndamento);
}
