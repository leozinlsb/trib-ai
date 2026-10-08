package br.com.tribia.service.painel;

import br.com.tribia.model.Calculo;
import br.com.tribia.model.Classificacao;
import br.com.tribia.model.Item;
import br.com.tribia.model.Nota;
import br.com.tribia.repository.CalculoRepository;
import br.com.tribia.repository.ClassificacaoRepository;
import br.com.tribia.repository.ItemRepository;
import br.com.tribia.security.AcessoService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Lê, de uma vez, os itens de um cliente no período com nota, classificação e cálculo (painel e relatório). */
@Component
public class DadosApuracao {

    /**
     * @param classificacao null se o item ainda não foi classificado
     * @param calculo       null se a nota não foi calculada (ou o item está sem classificação)
     */
    public record Linha(Item item, Nota nota, Classificacao classificacao, Calculo calculo) {
    }

    private final ItemRepository itemRepository;
    private final ClassificacaoRepository classificacaoRepository;
    private final CalculoRepository calculoRepository;
    private final AcessoService acesso;

    public DadosApuracao(ItemRepository itemRepository, ClassificacaoRepository classificacaoRepository,
                         CalculoRepository calculoRepository, AcessoService acesso) {
        this.itemRepository = itemRepository;
        this.classificacaoRepository = classificacaoRepository;
        this.calculoRepository = calculoRepository;
        this.acesso = acesso;
    }

    /** Competências AAAA-MM; null não filtra. Ordem: data da nota, nota, nItem. */
    @Transactional(readOnly = true)
    public List<Linha> doCliente(Long clienteId, String de, String ate) {
        acesso.clienteAcessivel(clienteId);
        return montar(itemRepository.doClienteNoPeriodo(clienteId, de, ate));
    }

    @Transactional(readOnly = true)
    public List<Linha> daNota(Long notaId) {
        acesso.notaAcessivel(notaId);
        return montar(itemRepository.daNota(notaId));
    }

    private List<Linha> montar(List<Item> itens) {
        List<Long> ids = itens.stream().map(Item::getId).toList();
        Map<Long, Classificacao> classificacoes = classificacaoRepository.findByItemIdIn(ids).stream()
                .collect(Collectors.toMap(c -> c.getItem().getId(), Function.identity()));
        Map<Long, Calculo> calculos = calculoRepository.findByItemIdIn(ids).stream()
                .collect(Collectors.toMap(c -> c.getItem().getId(), Function.identity()));
        return itens.stream()
                .map(i -> new Linha(i, i.getNota(), classificacoes.get(i.getId()), calculos.get(i.getId())))
                .toList();
    }
}
