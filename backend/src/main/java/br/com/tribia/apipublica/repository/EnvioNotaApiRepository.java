package br.com.tribia.apipublica.repository;

import br.com.tribia.apipublica.model.EnvioNotaApi;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Toda leitura pública filtra pela empresa da chave: o id público sozinho nunca basta. */
public interface EnvioNotaApiRepository extends JpaRepository<EnvioNotaApi, Long> {

    @Query("""
            select e from EnvioNotaApi e join fetch e.nota
            where e.publicoId = :publicoId and e.cliente.id = :clienteId
            """)
    Optional<EnvioNotaApi> buscarDaEmpresa(@Param("publicoId") String publicoId, @Param("clienteId") Long clienteId);

    @Query("""
            select e from EnvioNotaApi e join fetch e.nota
            where e.chave.id = :chaveId and e.idempotencyKey = :idempotencyKey
            """)
    Optional<EnvioNotaApi> buscarPorIdempotencia(@Param("chaveId") Long chaveId,
                                                 @Param("idempotencyKey") String idempotencyKey);

    /** Envio pela API de uma nota já importada (para apontar o id quando a mesma NF-e é reenviada). */
    @Query("select e from EnvioNotaApi e where e.nota.id = :notaId and e.cliente.id = :clienteId")
    Optional<EnvioNotaApi> buscarPorNota(@Param("notaId") Long notaId, @Param("clienteId") Long clienteId);

    @Query(value = """
            select e from EnvioNotaApi e join fetch e.nota
            where e.cliente.id = :clienteId and (:referencia is null or e.referenciaExterna = :referencia)
            """,
            countQuery = """
                    select count(e) from EnvioNotaApi e
                    where e.cliente.id = :clienteId and (:referencia is null or e.referenciaExterna = :referencia)
                    """)
    Page<EnvioNotaApi> listarDaEmpresa(@Param("clienteId") Long clienteId, @Param("referencia") String referencia,
                                      Pageable pagina);

    @Query("select coalesce(sum(e.itensIa), 0) from EnvioNotaApi e where e.chave.id = :chaveId and e.criadoEm >= :desde")
    long somarItensIaDesde(@Param("chaveId") Long chaveId, @Param("desde") Instant desde);

    @Query("""
            select count(e) from EnvioNotaApi e
            where e.chave.id = :chaveId and e.status = br.com.tribia.apipublica.model.EnvioNotaApi.Status.EM_PROCESSAMENTO
            """)
    long contarEmProcessamento(@Param("chaveId") Long chaveId);

    /** Envios que estavam em processamento quando o servidor parou (retomados na inicialização). */
    @Query("""
            select e.id from EnvioNotaApi e
            where e.status = br.com.tribia.apipublica.model.EnvioNotaApi.Status.EM_PROCESSAMENTO
            order by e.criadoEm, e.id
            """)
    List<Long> idsEmProcessamento();
}
