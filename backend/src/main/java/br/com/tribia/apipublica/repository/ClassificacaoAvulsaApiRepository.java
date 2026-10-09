package br.com.tribia.apipublica.repository;

import br.com.tribia.apipublica.model.ClassificacaoAvulsaApi;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Toda leitura pública filtra pela empresa da chave: o id público sozinho nunca basta. */
public interface ClassificacaoAvulsaApiRepository extends JpaRepository<ClassificacaoAvulsaApi, Long> {

    @Query("select coalesce(sum(c.itensIa), 0) from ClassificacaoAvulsaApi c where c.chave.id = :chaveId and c.criadoEm >= :desde")
    long somarItensIaDesde(@Param("chaveId") Long chaveId, @Param("desde") Instant desde);

    @Query("select c from ClassificacaoAvulsaApi c where c.publicoId = :publicoId and c.cliente.id = :clienteId")
    Optional<ClassificacaoAvulsaApi> buscarDaEmpresa(@Param("publicoId") String publicoId,
                                                     @Param("clienteId") Long clienteId);

    @Query("select c from ClassificacaoAvulsaApi c where c.chave.id = :chaveId and c.idempotencyKey = :idempotencyKey")
    Optional<ClassificacaoAvulsaApi> buscarPorIdempotencia(@Param("chaveId") Long chaveId,
                                                           @Param("idempotencyKey") String idempotencyKey);

    @Query("""
            select count(c) from ClassificacaoAvulsaApi c where c.chave.id = :chaveId
              and c.status = br.com.tribia.apipublica.model.ClassificacaoAvulsaApi.Status.EM_PROCESSAMENTO
            """)
    long contarEmProcessamento(@Param("chaveId") Long chaveId);

    /** Pedidos que estavam em processamento quando o servidor parou (retomados na inicialização). */
    @Query("""
            select c.id from ClassificacaoAvulsaApi c
            where c.status = br.com.tribia.apipublica.model.ClassificacaoAvulsaApi.Status.EM_PROCESSAMENTO
            order by c.criadoEm, c.id
            """)
    List<Long> idsEmProcessamento();
}
