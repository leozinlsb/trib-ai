package br.com.tribia.service.tabelas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Nomenclatura Comum do Mercosul vigente (dados-oficiais/ncm-vigente.csv, extraída do Portal Único Siscomex por
 * ferramentas/atualizar_ncm.mjs). Diz se um código de 8 dígitos existe na NCM e desde quando, com o texto oficial
 * de cada nível (capítulo, posição, subposição, item, subitem).
 *
 * Limites da fonte, que quem consulta precisa respeitar:
 * <ul>
 *   <li>Só traz os códigos vigentes na data da extração ({@link #versao()}). Código ausente "não consta da NCM vigente":
 *       pode ter sido extinto ou nunca ter existido; a tabela não diz qual.</li>
 *   <li>Não traz histórico de alterações: para datas anteriores ao início de vigência, o código ainda não existia;
 *       para datas posteriores à extração, a vigência futura não é garantida.</li>
 *   <li>NCM não é TIPI nem enquadramento tributário: existir na NCM não diz nada sobre IPI, IBS/CBS ou benefícios.</li>
 * </ul>
 */
@Component
public class TabelaNcmVigente {

    /** Um código da NCM (qualquer nível). fim null = sem data de término na fonte. */
    public record Codigo(String codigo, String descricao, LocalDate inicio, LocalDate fim, String ato) {
    }

    /** Versão da tabela embarcada (metadados da extração). */
    public record Versao(String fonte, String url, String situacao, String ato, String extraidoEm) {
    }

    public enum Situacao {
        /** Consta da NCM vigente e já valia na data consultada. */
        VIGENTE,
        /** Consta da NCM vigente, mas só passou a valer depois da data consultada. */
        NAO_VIGENTE_NA_DATA,
        /** Não consta da NCM vigente na data da extração (extinto ou inexistente: a fonte não distingue). */
        NAO_CONSTA,
        /** Não tem 8 dígitos. */
        FORMATO_INVALIDO
    }

    public record Consulta(Situacao situacao, Codigo codigo, String descricaoCompleta) {
    }

    private final Map<String, Codigo> codigos;
    private final Versao versao;

    public TabelaNcmVigente(ObjectMapper json) {
        this.codigos = ler("ncm-vigente.csv");
        this.versao = lerVersao(json);
    }

    public Versao versao() {
        return versao;
    }

    /**
     * @param ncm  8 dígitos (pontos e espaços são ignorados)
     * @param data data fiscal relevante (ex.: data da análise ou do fato gerador)
     */
    public Consulta consultar(String ncm, LocalDate data) {
        String n = ncm == null ? "" : ncm.replaceAll("\\D", "");
        if (n.length() != 8) {
            return new Consulta(Situacao.FORMATO_INVALIDO, null, null);
        }
        Codigo c = codigos.get(n);
        if (c == null) {
            return new Consulta(Situacao.NAO_CONSTA, null, null);
        }
        boolean comecou = c.inicio() == null || !data.isBefore(c.inicio());
        boolean terminou = c.fim() != null && data.isAfter(c.fim());
        return new Consulta(comecou && !terminou ? Situacao.VIGENTE : Situacao.NAO_VIGENTE_NA_DATA, c, descricaoCompleta(n));
    }

    public Optional<Codigo> buscar(String codigo) {
        return Optional.ofNullable(codigos.get(codigo == null ? "" : codigo.replaceAll("\\D", "")));
    }

    /**
     * Texto oficial montado pela hierarquia (capítulo → posição → subposições → item → subitem), porque o texto do
     * subitem sozinho costuma ser só "Outros". Ex.: 3401.11.90 → "Sabões... › -- De toucador... › Outros".
     */
    String descricaoCompleta(String ncm8) {
        List<String> partes = new ArrayList<>();
        for (int tamanho : new int[]{4, 5, 6, 7, 8}) {
            Codigo nivel = codigos.get(ncm8.substring(0, tamanho));
            if (nivel != null && !nivel.descricao().isBlank()) {
                partes.add(nivel.descricao());
            }
        }
        return String.join(" › ", partes);
    }

    int tamanho() {
        return codigos.size();
    }

    // ---------------- leitura ----------------

    private static Map<String, Codigo> ler(String recurso) {
        InputStream in = TabelaNcmVigente.class.getResourceAsStream("/dados-oficiais/" + recurso);
        if (in == null) {
            throw new IllegalStateException("Tabela oficial não encontrada: dados-oficiais/" + recurso);
        }
        Map<String, Codigo> m = new HashMap<>(20_000);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String linha = r.readLine(); // cabeçalho
            if (linha == null || !linha.startsWith("codigo;descricao;data_inicio;data_fim;ato")) {
                throw new IllegalStateException("Cabeçalho inesperado em " + recurso);
            }
            while ((linha = r.readLine()) != null) {
                if (linha.isBlank()) {
                    continue;
                }
                List<String> c = campos(linha);
                if (c.size() != 5) {
                    throw new IllegalStateException("Linha malformada em " + recurso + ": " + linha);
                }
                m.put(c.get(0), new Codigo(c.get(0), c.get(1), data(c.get(2)), data(c.get(3)), c.get(4)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Map.copyOf(m);
    }

    /** Separador ";" com campos opcionalmente entre aspas (aspas internas dobradas), como o extrator grava. */
    static List<String> campos(String linha) {
        List<String> campos = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        boolean entreAspas = false;
        for (int i = 0; i < linha.length(); i++) {
            char ch = linha.charAt(i);
            if (entreAspas) {
                if (ch == '"' && i + 1 < linha.length() && linha.charAt(i + 1) == '"') {
                    atual.append('"');
                    i++;
                } else if (ch == '"') {
                    entreAspas = false;
                } else {
                    atual.append(ch);
                }
            } else if (ch == '"') {
                entreAspas = true;
            } else if (ch == ';') {
                campos.add(atual.toString());
                atual.setLength(0);
            } else {
                atual.append(ch);
            }
        }
        campos.add(atual.toString());
        return campos;
    }

    private static LocalDate data(String s) {
        return s == null || s.isBlank() ? null : LocalDate.parse(s.trim());
    }

    private static Versao lerVersao(ObjectMapper json) {
        try (InputStream in = TabelaNcmVigente.class.getResourceAsStream("/dados-oficiais/ncm-vigente.json")) {
            if (in == null) {
                throw new IllegalStateException("Metadados da NCM não encontrados: dados-oficiais/ncm-vigente.json");
            }
            JsonNode n = json.readTree(in);
            return new Versao(n.path("fonte").asText(), n.path("url").asText(), n.path("situacao").asText(),
                    n.path("ato").asText(), n.path("extraidoEm").asText());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
