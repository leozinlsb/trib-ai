package br.com.tribia.controller;

import br.com.tribia.security.AcessoService;
import br.com.tribia.service.tabelas.VerificacaoTabelaNcm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Situação das tabelas oficiais embarcadas (somente ADMIN). */
@RestController
@Tag(name = "Dados oficiais")
public class DadosOficiaisController {

    private final VerificacaoTabelaNcm ncm;
    private final AcessoService acesso;

    public DadosOficiaisController(VerificacaoTabelaNcm ncm, AcessoService acesso) {
        this.ncm = ncm;
        this.acesso = acesso;
    }

    @Operation(summary = "Versão e idade da tabela NCM vigente embarcada")
    @GetMapping("/api/admin/tabelas/ncm")
    public VerificacaoTabelaNcm.Situacao tabelaNcm() {
        acesso.exigirAdmin();
        return ncm.situacao();
    }
}
