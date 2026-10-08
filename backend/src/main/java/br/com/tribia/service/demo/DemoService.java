package br.com.tribia.service.demo;

import br.com.tribia.config.CalculadoraProperties;
import br.com.tribia.config.CalculoProperties;
import br.com.tribia.config.LlmProperties;
import br.com.tribia.repository.CalculoRepository;
import br.com.tribia.repository.ClassificacaoCacheRepository;
import br.com.tribia.repository.ClassificacaoRepository;
import br.com.tribia.repository.ClienteRepository;
import br.com.tribia.repository.ItemRepository;
import br.com.tribia.repository.NotaRepository;
import br.com.tribia.security.AcessoService;
import br.com.tribia.service.classificacao.RespostasGravadasIa;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;

/** Apoio à apresentação (profile demo): checklist antes de começar e volta ao estado inicial entre ensaios. */
@Service
public class DemoService {

    private static final Logger log = LoggerFactory.getLogger(DemoService.class);

    /**
     * @param calculadoraNoAr     a calculadora oficial aceita conexão (senão o cálculo usa o método simplificado)
     * @param iaConfigurada       há chave da IA (GEMINI_API_KEY)
     * @param respostasGravadasIa quantos produtos têm resposta gravada da IA (plano B)
     */
    public record Status(boolean calculadoraNoAr, String modoCalculo, boolean iaConfigurada, int respostasGravadasIa,
                         long clientes, long notas, long itens, long classificacoes, long calculos) {
    }

    private final ClienteRepository clienteRepository;
    private final NotaRepository notaRepository;
    private final ItemRepository itemRepository;
    private final ClassificacaoRepository classificacaoRepository;
    private final CalculoRepository calculoRepository;
    private final ClassificacaoCacheRepository cacheRepository;
    private final SeedService seedService;
    private final RespostasGravadasIa respostasGravadas;
    private final CalculadoraProperties calculadora;
    private final CalculoProperties calculo;
    private final LlmProperties llm;
    private final TransactionTemplate transacao;
    private final AcessoService acesso;

    public DemoService(ClienteRepository clienteRepository, NotaRepository notaRepository, ItemRepository itemRepository,
                       ClassificacaoRepository classificacaoRepository, CalculoRepository calculoRepository,
                       ClassificacaoCacheRepository cacheRepository, SeedService seedService,
                       RespostasGravadasIa respostasGravadas, CalculadoraProperties calculadora, CalculoProperties calculo,
                       LlmProperties llm, TransactionTemplate transacao, AcessoService acesso) {
        this.clienteRepository = clienteRepository;
        this.notaRepository = notaRepository;
        this.itemRepository = itemRepository;
        this.classificacaoRepository = classificacaoRepository;
        this.calculoRepository = calculoRepository;
        this.cacheRepository = cacheRepository;
        this.seedService = seedService;
        this.respostasGravadas = respostasGravadas;
        this.calculadora = calculadora;
        this.calculo = calculo;
        this.llm = llm;
        this.transacao = transacao;
        this.acesso = acesso;
    }

    public Status status() {
        acesso.exigirAdmin();
        return new Status(calculadoraNoAr(), calculo.modo().name(), llm.configurada(), respostasGravadas.quantidade(),
                clienteRepository.count(), notaRepository.count(), itemRepository.count(),
                classificacaoRepository.count(), calculoRepository.count());
    }

    /**
     * Apaga notas, classificações, cálculos e o cache (inclusive o que a IA e a revisão gravaram) e recarrega o seed.
     * Os clientes ficam. Os ids novos continuam a numeração (não voltam a 1).
     */
    public SeedService.Resultado reiniciar() {
        acesso.exigirAdmin();
        transacao.executeWithoutResult(s -> {
            calculoRepository.deleteAllInBatch();
            classificacaoRepository.deleteAllInBatch();
            itemRepository.deleteAllInBatch();
            notaRepository.deleteAllInBatch();
            cacheRepository.deleteAllInBatch();
        });
        SeedService.Resultado r = seedService.carregar();
        log.info("Demo: reiniciada ({} notas do seed)", r.notasImportadas());
        return r;
    }

    private boolean calculadoraNoAr() {
        URI uri = URI.create(calculadora.url());
        int porta = uri.getPort() != -1 ? uri.getPort() : ("https".equals(uri.getScheme()) ? 443 : 80);
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(uri.getHost(), porta), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
