package br.com.tribia.apipublica.repository;

import br.com.tribia.apipublica.model.ChaveApi;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChaveApiRepository extends JpaRepository<ChaveApi, Long> {

    @Query("select c from ChaveApi c join fetch c.cliente where c.prefixo = :prefixo")
    Optional<ChaveApi> buscarPorPrefixo(@Param("prefixo") String prefixo);

    boolean existsByPrefixo(String prefixo);

    /** Para a listagem do administrador; null = todas as empresas. */
    @Query("""
            select c from ChaveApi c join fetch c.cliente
            where (:clienteId is null or c.cliente.id = :clienteId)
            order by c.criadaEm desc, c.id desc
            """)
    List<ChaveApi> listar(@Param("clienteId") Long clienteId);

    /** Grava o último uso no máximo uma vez por janela (evita uma escrita por requisição). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ChaveApi c set c.ultimoUsoEm = :agora
            where c.id = :id and (c.ultimoUsoEm is null or c.ultimoUsoEm < :limite)
            """)
    int registrarUso(@Param("id") Long id, @Param("agora") Instant agora, @Param("limite") Instant limite);
}
