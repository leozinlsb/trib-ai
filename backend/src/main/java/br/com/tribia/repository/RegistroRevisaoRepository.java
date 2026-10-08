package br.com.tribia.repository;

import br.com.tribia.model.RegistroRevisao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegistroRevisaoRepository extends JpaRepository<RegistroRevisao, Long> {

    List<RegistroRevisao> findByItemIdOrderByIdDesc(Long itemId);
}
