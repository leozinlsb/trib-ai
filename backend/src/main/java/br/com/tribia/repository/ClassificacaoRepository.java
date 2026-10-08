package br.com.tribia.repository;

import br.com.tribia.model.Classificacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClassificacaoRepository extends JpaRepository<Classificacao, Long> {

    Optional<Classificacao> findByItemId(Long itemId);

    List<Classificacao> findByItemIdIn(Collection<Long> itemIds);
}
