package br.com.tribia.apipublica.repository;

import br.com.tribia.apipublica.model.SolicitacaoApi;
import br.com.tribia.model.StatusAnalise;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

/** Toda leitura pública filtra pela empresa da chave: o id público sozinho nunca basta. */
public interface SolicitacaoApiRepository extends JpaRepository<SolicitacaoApi, Long> {

    @Query("""
            select s from SolicitacaoApi s join fetch s.analise
            where s.publicoId = :publicoId and s.cliente.id = :clienteId
            """)
    Optional<SolicitacaoApi> buscarDaEmpresa(@Param("publicoId") String publicoId, @Param("clienteId") Long clienteId);

    @Query("""
            select s from SolicitacaoApi s join fetch s.analise
            where s.chave.id = :chaveId and s.idempotencyKey = :idempotencyKey
            """)
    Optional<SolicitacaoApi> buscarPorIdempotencia(@Param("chaveId") Long chaveId,
                                                   @Param("idempotencyKey") String idempotencyKey);

    @Query(value = """
            select s from SolicitacaoApi s join fetch s.analise
            where s.cliente.id = :clienteId and (:referencia is null or s.referenciaExterna = :referencia)
            """,
            countQuery = """
                    select count(s) from SolicitacaoApi s
                    where s.cliente.id = :clienteId and (:referencia is null or s.referenciaExterna = :referencia)
                    """)
    Page<SolicitacaoApi> listarDaEmpresa(@Param("clienteId") Long clienteId, @Param("referencia") String referencia,
                                        Pageable pagina);

    @Query("select count(s) from SolicitacaoApi s where s.chave.id = :chaveId and s.criadaEm >= :desde")
    long contarCriadasDesde(@Param("chaveId") Long chaveId, @Param("desde") Instant desde);

    @Query("select count(s) from SolicitacaoApi s where s.chave.id = :chaveId and s.analise.status in :emAndamento")
    long contarEmAndamento(@Param("chaveId") Long chaveId,
                           @Param("emAndamento") Collection<StatusAnalise> emAndamento);
}
