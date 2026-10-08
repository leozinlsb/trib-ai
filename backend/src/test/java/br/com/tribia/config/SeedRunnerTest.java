package br.com.tribia.config;

import br.com.tribia.Fixtures;
import br.com.tribia.model.Nota;
import br.com.tribia.model.OrigemClassificacao;
import br.com.tribia.model.TipoNota;
import br.com.tribia.repository.CalculoRepository;
import br.com.tribia.repository.ClassificacaoRepository;
import br.com.tribia.repository.ItemRepository;
import br.com.tribia.repository.NotaRepository;
import br.com.tribia.service.NotaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Sobe a aplicação com o seed ligado (padrão), calculando pelo método simplificado (determinístico). */
@SpringBootTest(properties = "tribia.calculo.modo=SIMPLIFICADA")
class SeedRunnerTest {

    @Autowired
    NotaRepository notaRepository;

    @Autowired
    NotaService notaService;

    @Autowired
    ItemRepository itemRepository;

    @Autowired
    ClassificacaoRepository classificacaoRepository;

    @Autowired
    CalculoRepository calculoRepository;

    @Test
    void todoItemDoSeedFoiClassificadoPeloCacheECalculado() {
        long itens = itemRepository.count();

        assertThat(itens).isGreaterThan(50);
        assertThat(classificacaoRepository.count()).as("classificações").isEqualTo(itens);
        assertThat(calculoRepository.count()).as("cálculos").isEqualTo(itens);
        assertThat(classificacaoRepository.findAll()).allSatisfy(c ->
                assertThat(c.getOrigem()).isEqualTo(OrigemClassificacao.CACHE));
    }

    @Test
    void todosOsXmlsDoSeedForamImportados() throws IOException {
        int arquivos = new PathMatchingResourcePatternResolver().getResources("classpath*:seed/*/*.xml").length;

        assertThat(arquivos).isGreaterThanOrEqualTo(18);
        assertThat(notaRepository.count()).isEqualTo(arquivos);
    }

    @Test
    void cadaClienteTemSaidasEEntradasEmMaisDeUmMes() {
        for (long clienteId = 1; clienteId <= 3; clienteId++) {
            List<Nota> notas = notaRepository.buscar(clienteId, null, null);

            assertThat(notas.stream().filter(n -> n.getTipo() == TipoNota.SAIDA).count())
                    .as("saídas do cliente %d", clienteId).isGreaterThanOrEqualTo(3);
            assertThat(notas.stream().filter(n -> n.getTipo() == TipoNota.ENTRADA).count())
                    .as("entradas do cliente %d", clienteId).isGreaterThanOrEqualTo(3);
            assertThat(notas.stream().map(Nota::getCompetencia).distinct().count())
                    .as("competências do cliente %d", clienteId).isGreaterThanOrEqualTo(2);
        }
    }

    /** As notas reservadas para a demo não podem estar no seed, senão o upload ao vivo dá 409. */
    @Test
    @Transactional
    void notasDoUploadAoVivoNaoEstaoNoSeed() throws IOException {
        Path pasta = Path.of("notas-demo-ao-vivo");
        List<Path> xmls;
        try (Stream<Path> s = Files.list(pasta)) {
            xmls = s.filter(p -> p.toString().endsWith(".xml")).sorted().toList();
        }
        assertThat(xmls).hasSize(4);

        for (Path xml : xmls) {
            long clienteId = Long.parseLong(xml.getFileName().toString().substring(0, 1));
            Nota nota = notaService.importar(clienteId, Files.readAllBytes(xml));
            assertThat(nota.getTipo()).as(xml.toString()).isEqualTo(TipoNota.SAIDA);
        }
        assertThat(xmls).anySatisfy(x -> assertThat(x.getFileName().toString()).endsWith(Fixtures.NFE_SAIDA_HACKATHON));
    }
}
