package br.com.tribia.repository;

import br.com.tribia.model.ClassificacaoCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClassificacaoCacheRepository extends JpaRepository<ClassificacaoCache, Long> {

    Optional<ClassificacaoCache> findByChave(String chave);
}
