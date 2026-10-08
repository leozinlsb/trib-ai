package br.com.tribia.repository;

import br.com.tribia.model.Papel;
import br.com.tribia.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /** Com a empresa carregada (o principal da sessão precisa do id dela). */
    @Query("select u from Usuario u left join fetch u.cliente where lower(u.email) = lower(:email)")
    Optional<Usuario> buscarPorEmail(@Param("email") String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPapel(Papel papel);

    List<Usuario> findByClienteIdOrderByNome(Long clienteId);
}
