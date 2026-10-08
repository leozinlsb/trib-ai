package br.com.tribia.controller;

import br.com.tribia.service.demo.DemoService;
import br.com.tribia.service.demo.SeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Só existe com tribia.demo.habilitado=true (profile demo): nunca em produção. */
@RestController
@Tag(name = "Demonstração")
@ConditionalOnProperty(name = "tribia.demo.habilitado", havingValue = "true")
public class DemoController {

    private final DemoService demo;

    public DemoController(DemoService demo) {
        this.demo = demo;
    }

    @Operation(summary = "Checklist antes da apresentação: calculadora, IA, plano B e volume de dados")
    @GetMapping("/api/demo/status")
    public DemoService.Status status() {
        return demo.status();
    }

    @Operation(summary = "Volta ao estado inicial (só o seed), apagando uploads, revisões e o que a IA gravou no cache")
    @PostMapping("/api/demo/reiniciar")
    public SeedService.Resultado reiniciar() {
        return demo.reiniciar();
    }
}
