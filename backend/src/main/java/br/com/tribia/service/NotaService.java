package br.com.tribia.service;

import br.com.tribia.dto.NotaDetalheDto;
import br.com.tribia.dto.NotaResumoDto;
import br.com.tribia.exception.ApiException;
import br.com.tribia.exception.NotaRejeitadaException;
import br.com.tribia.exception.RecursoNaoEncontradoException;
import br.com.tribia.model.Cliente;
import br.com.tribia.model.Nota;
import br.com.tribia.model.TipoNota;
import br.com.tribia.repository.NotaRepository;
import br.com.tribia.service.nfe.NfeLida;
import br.com.tribia.util.CnpjUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotaService {

    private final NotaRepository repository;
    private final ClienteService clienteService;
    private final ParserNfeService parser;

    public NotaService(NotaRepository repository, ClienteService clienteService, ParserNfeService parser) {
        this.repository = repository;
        this.clienteService = clienteService;
        this.parser = parser;
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
        NfeLida lida = parser.ler(xml);

        validarEscopo(lida);
        TipoNota tipo = detectarTipo(cliente, lida);

        if (repository.existsByClienteIdAndChave(clienteId, lida.chave())) {
            throw NotaRejeitadaException.duplicada("A nota " + lida.chave() + " já foi importada para este cliente.");
        }
        return repository.save(Nota.de(cliente, tipo, lida));
    }

    /** Somente NF-e modelo 55, finalidade normal, emitida como saída pelo emitente. */
    private void validarEscopo(NfeLida lida) {
        if (!"55".equals(lida.modelo())) {
            throw NotaRejeitadaException.invalida(
                    "Somente NF-e modelo 55 é aceita (modelo recebido: " + lida.modelo() + ").");
        }
        Integer fin = lida.finalidade();
        if (fin == null || fin != 1) {
            String nome = switch (fin == null ? 0 : fin) {
                case 2 -> "complementar";
                case 3 -> "de ajuste";
                case 4 -> "de devolução";
                default -> "com finalidade " + fin;
            };
            throw NotaRejeitadaException.invalida(
                    "Notas " + nome + " (finNFe=" + fin + ") ainda não são suportadas. Envie apenas notas normais.");
        }
        // tpNF=0 é nota de entrada emitida pelo próprio emitente (importação, compra de produtor etc.).
        // Pelo CNPJ ela seria lida ao contrário (o emitente é quem compra), então fica fora do MVP.
        if (lida.tipoOperacao() == null || lida.tipoOperacao() != 1) {
            throw NotaRejeitadaException.invalida(
                    "Notas de entrada emitidas pelo próprio emitente (tpNF=0) ainda não são suportadas.");
        }
        if (lida.emitente() == null || lida.emitente().documento() == null) {
            throw NotaRejeitadaException.invalida("Emitente sem CNPJ/CPF.");
        }
        if (lida.dataEmissao() == null || lida.valorTotal() == null) {
            throw NotaRejeitadaException.invalida("Nota sem data de emissão ou sem valor total (vNF).");
        }
        // TODO conferir se a chave bate com emitente/modelo/série/número; hoje o parser só valida o dígito verificador
    }

    /**
     * Emitente = cliente: SAIDA. Destinatário = cliente: ENTRADA. Nenhum dos dois: 422.
     * Se emitente e destinatário forem o próprio cliente (transferência), vale SAIDA.
     */
    static TipoNota detectarTipo(Cliente cliente, NfeLida lida) {
        String cnpjCliente = CnpjUtil.somenteDigitos(cliente.getCnpj());
        if (cnpjCliente.equals(lida.emitente().documento())) {
            return TipoNota.SAIDA;
        }
        if (lida.destinatario() != null && cnpjCliente.equals(lida.destinatario().documento())) {
            return TipoNota.ENTRADA;
        }
        throw NotaRejeitadaException.invalida("Esta nota não pertence ao cliente");
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
        return repository.buscarComItens(notaId)
                .map(NotaDetalheDto::de)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Nota " + notaId + " não encontrada"));
    }
}
