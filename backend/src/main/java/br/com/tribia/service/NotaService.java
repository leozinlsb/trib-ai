package br.com.tribia.service;

import br.com.tribia.dto.CalculoDto;
import br.com.tribia.dto.ClassificacaoDto;
import br.com.tribia.dto.ItemDto;
import br.com.tribia.dto.NotaDetalheDto;
import br.com.tribia.dto.NotaResumoDto;
import br.com.tribia.exception.ApiException;
import br.com.tribia.exception.NotaRejeitadaException;
import br.com.tribia.exception.RecursoNaoEncontradoException;
import br.com.tribia.model.Calculo;
import br.com.tribia.model.Classificacao;
import br.com.tribia.model.Cliente;
import br.com.tribia.model.Item;
import br.com.tribia.model.Nota;
import br.com.tribia.model.Operacao;
import br.com.tribia.model.TipoNota;
import br.com.tribia.repository.CalculoRepository;
import br.com.tribia.repository.ClassificacaoRepository;
import br.com.tribia.repository.NotaRepository;
import br.com.tribia.security.AcessoService;
import br.com.tribia.service.nfe.NfeLida;
import br.com.tribia.service.tabelas.TabelaCClassTrib;
import br.com.tribia.util.CnpjUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NotaService {

    private final NotaRepository repository;
    private final ClienteService clienteService;
    private final ParserNfeService parser;
    private final ClassificacaoRepository classificacaoRepository;
    private final CalculoRepository calculoRepository;
    private final TabelaCClassTrib tabela;
    private final AcessoService acesso;

    public NotaService(NotaRepository repository, ClienteService clienteService, ParserNfeService parser,
                       ClassificacaoRepository classificacaoRepository, CalculoRepository calculoRepository,
                       TabelaCClassTrib tabela, AcessoService acesso) {
        this.repository = repository;
        this.clienteService = clienteService;
        this.parser = parser;
        this.classificacaoRepository = classificacaoRepository;
        this.calculoRepository = calculoRepository;
        this.tabela = tabela;
        this.acesso = acesso;
    }

    /**
     * Importa um XML para o cliente. Usado pelo upload e pelo seed da inicialização.
     * Cada chamada é uma transação: no upload em lote, um arquivo rejeitado não desfaz os outros.
     *
     * @throws NotaRejeitadaException 422 (inválida ou fora do MVP) ou 409 (duplicada)
     */
    @Transactional
    public Nota importar(Long clienteId, byte[] xml) {
        Cliente cliente = clienteService.buscar(clienteId);
        if (!cliente.isAtivo()) {
            throw new ApiException(HttpStatus.CONFLICT, "Empresa desativada",
                    "Esta empresa está desativada. Reative-a para enviar notas.");
        }
        NfeLida lida = parser.ler(xml);

        validarEscopo(lida);
        Operacao operacao = detectarOperacao(cliente, lida);

        if (repository.existsByClienteIdAndChave(clienteId, lida.chave())) {
            throw NotaRejeitadaException.duplicada("A nota " + lida.chave() + " já foi importada para este cliente.");
        }
        return repository.save(Nota.de(cliente, operacao, lida));
    }

    /**
     * NF-e modelo 55 normal (1), complementar (2) ou de devolução (4), de saída ou de entrada (tpNF 0 ou 1).
     * Nota de ajuste (3) fica de fora: é lançamento fiscal sem circulação de mercadoria.
     */
    private void validarEscopo(NfeLida lida) {
        if (!"55".equals(lida.modelo())) {
            throw NotaRejeitadaException.invalida(
                    "Somente NF-e modelo 55 é aceita (modelo recebido: " + lida.modelo() + ").");
        }
        Integer fin = lida.finalidade();
        if (fin == null || (fin != 1 && fin != 2 && fin != 4)) {
            String nome = fin != null && fin == 3 ? "de ajuste" : "com finalidade " + fin;
            throw NotaRejeitadaException.invalida("Notas " + nome + " (finNFe=" + fin + ") não são suportadas: "
                    + "elas não movimentam mercadoria. Envie notas normais, complementares ou de devolução.");
        }
        if (lida.tipoOperacao() == null || (lida.tipoOperacao() != 0 && lida.tipoOperacao() != 1)) {
            throw NotaRejeitadaException.invalida("Tipo de operação (tpNF) inválido: " + lida.tipoOperacao());
        }
        if (lida.emitente() == null || lida.emitente().documento() == null) {
            throw NotaRejeitadaException.invalida("Emitente sem CNPJ/CPF.");
        }
        if (lida.dataEmissao() == null || lida.valorTotal() == null) {
            throw NotaRejeitadaException.invalida("Nota sem data de emissão ou sem valor total (vNF).");
        }
    }

    /**
     * O cliente tem de ser o emitente ou o destinatário (senão, 422). A operação sai do sentido da mercadoria
     * (tpNF + quem emitiu) e da finalidade: venda, compra, devolução de venda ou devolução de compra.
     * Se emitente e destinatário forem o próprio cliente (transferência), vale como emitida por ele.
     */
    static Operacao detectarOperacao(Cliente cliente, NfeLida lida) {
        String cnpjCliente = CnpjUtil.somenteDigitos(cliente.getCnpj());
        boolean emitente = cnpjCliente.equals(lida.emitente().documento());
        boolean destinatario = lida.destinatario() != null && cnpjCliente.equals(lida.destinatario().documento());
        if (!emitente && !destinatario) {
            throw NotaRejeitadaException.invalida("Esta nota não pertence ao cliente");
        }
        return Operacao.de(emitente, lida.tipoOperacao(), lida.finalidade() == 4);
    }

    @Transactional(readOnly = true)
    public List<NotaResumoDto> listar(Long clienteId, TipoNota tipo, String competencia) {
        clienteService.buscar(clienteId);
        if (competencia != null && !competencia.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw ApiException.requisicaoInvalida("Competência deve estar no formato AAAA-MM: " + competencia);
        }
        return repository.buscar(clienteId, tipo, competencia).stream().map(NotaResumoDto::de).toList();
    }

    @Transactional(readOnly = true)
    public NotaDetalheDto detalhar(Long notaId) {
        acesso.notaAcessivel(notaId);
        Nota nota = repository.buscarComItens(notaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Nota " + notaId + " não encontrada"));
        List<Long> ids = nota.getItens().stream().map(Item::getId).toList();
        Map<Long, Classificacao> classificacoes = classificacaoRepository.findByItemIdIn(ids).stream()
                .collect(Collectors.toMap(c -> c.getItem().getId(), Function.identity()));
        Map<Long, Calculo> calculos = calculoRepository.findByItemIdIn(ids).stream()
                .collect(Collectors.toMap(c -> c.getItem().getId(), Function.identity()));
        List<ItemDto> itens = nota.getItens().stream()
                .map(i -> ItemDto.de(i, ClassificacaoDto.de(classificacoes.get(i.getId()), tabela),
                        CalculoDto.de(calculos.get(i.getId()))))
                .toList();
        return NotaDetalheDto.de(nota, itens);
    }
}
