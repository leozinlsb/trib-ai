package br.com.tribia.controller;

import br.com.tribia.dto.CalculoClienteDto;
import br.com.tribia.dto.CalculoNotaDto;
import br.com.tribia.dto.ClassificacaoNotaDto;
import br.com.tribia.service.calculo.CalculoService;
import br.com.tribia.service.classificacao.ClassificacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@Tag(name = "Classificação e cálculo")
public class ApuracaoController {

    private final ClassificacaoService classificacaoService;
    private final CalculoService calculoService;

    public ApuracaoController(ClassificacaoService classificacaoService, CalculoService calculoService) {
        this.classificacaoService = classificacaoService;
        this.calculoService = calculoService;
    }

    @Operation(summary = "Classifica os itens da nota (CST + cClassTrib) e recalcula a nota",
            description = "Ordem: grupo IBS/CBS do XML, cache global por NCM + descrição e IA. Itens já classificados "
                    + "não são alterados. Em seguida recalcula a nota (mantendo o cenário de CBS), para o painel "
                    + "refletir; o resultado vem em 'calculo'. Use calcular=false para só classificar.")
    @PostMapping("/api/notas/{id}/classificar")
    public ClassificacaoNotaDto classificar(@PathVariable Long id,
                                            @RequestParam(defaultValue = "true") boolean calcular) {
        ClassificacaoNotaDto r = classificacaoService.classificar(id);
        return calcular ? r.comCalculo(calculoService.recalcular(id)) : r;
    }

    @Operation(summary = "Calcula CBS/IBS/IS de 2027 e o comparativo com hoje para os itens classificados",
            description = "Usa a calculadora oficial; se ela estiver fora do ar, o cálculo simplificado (com aviso).")
    @PostMapping("/api/notas/{id}/calcular")
    public CalculoNotaDto calcular(@PathVariable Long id,
                                   @Parameter(description = "Cenário: alíquota da CBS 2027 em % (ex.: 8.8). "
                                           + "Vazio usa a configurada.")
                                   @RequestParam(required = false) BigDecimal cbs) {
        return calculoService.calcular(id, cbs);
    }

    @Operation(summary = "Recalcula todas as notas do cliente (ex.: para trocar o cenário da CBS)")
    @PostMapping("/api/clientes/{id}/calcular")
    public CalculoClienteDto calcularCliente(@PathVariable Long id,
                                             @RequestParam(required = false) BigDecimal cbs) {
        return calculoService.calcularCliente(id, cbs);
    }
}
