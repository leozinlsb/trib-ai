package br.com.tribia.controller;

import br.com.tribia.dto.OpcaoClassificacaoDto;
import br.com.tribia.dto.RevisaoDto;
import br.com.tribia.dto.RevisaoResultadoDto;
import br.com.tribia.dto.RevisarItemRequest;
import br.com.tribia.service.classificacao.RevisaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Revisão")
public class RevisaoController {

    private final RevisaoService service;

    public RevisaoController(RevisaoService service) {
        this.service = service;
    }

    @Operation(summary = "Itens que precisam de revisão: sem classificação, não aceitos ou com confiança baixa",
            description = "Ordem: sem classificação primeiro, depois menor confiança, depois maior valor.")
    @GetMapping("/api/clientes/{id}/revisao")
    public RevisaoDto listar(@PathVariable Long id,
                             @RequestParam(required = false) String de,
                             @RequestParam(required = false) String ate) {
        return service.listar(id, de, ate);
    }

    @Operation(summary = "Aceita, corrige ou marca como uso e consumo a classificação de um item",
            description = "Por padrão aplica o mesmo aos itens idênticos (NCM + descrição) do cliente ainda não "
                    + "revisados. Recalcula as notas afetadas. A correção vai para o cache global como validada.")
    @PutMapping("/api/itens/{id}/classificacao")
    public RevisaoResultadoDto revisar(@PathVariable Long id, @RequestBody RevisarItemRequest req) {
        return service.revisar(id, req);
    }

    @Operation(summary = "Opções de classificação aceitas em NF-e (tabela oficial)",
            description = "Com ncm, as associadas a ele pela lista oficial vêm primeiro e marcadas.")
    @GetMapping("/api/classificacoes/opcoes")
    public List<OpcaoClassificacaoDto> opcoes(@Parameter(description = "NCM do item (opcional)")
                                              @RequestParam(required = false) String ncm) {
        return service.opcoes(ncm, false);
    }
}
