package br.com.tribia.apipublica.repository;

import br.com.tribia.apipublica.model.ClassificacaoAvulsaApi;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ClassificacaoAvulsaApiRepository extends JpaRepository<ClassificacaoAvulsaApi, Long> {

    @Query("select coalesce(sum(c.itensIa), 0) from ClassificacaoAvulsaApi c where c.chave.id = :chaveId and c.criadoEm >= :desde")
    long somarItensIaDesde(@Param("chaveId") Long chaveId, @Param("desde") Instant desde);
}
