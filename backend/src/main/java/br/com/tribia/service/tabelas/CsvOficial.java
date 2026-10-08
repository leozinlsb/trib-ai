package br.com.tribia.service.tabelas;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lê os CSVs de dados-oficiais/ (gerados por ferramentas/extrair_dados_oficiais.py): separador ";",
 * linhas iniciadas por "#" são comentário, a primeira linha restante é o cabeçalho.
 * Os textos já vêm sem ";" e sem aspas, então um split simples basta.
 */
final class CsvOficial {

    private CsvOficial() {
    }

    static List<Map<String, String>> ler(String recurso) {
        InputStream in = CsvOficial.class.getResourceAsStream("/dados-oficiais/" + recurso);
        if (in == null) {
            throw new IllegalStateException("Tabela oficial não encontrada: dados-oficiais/" + recurso);
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String[] cabecalho = null;
            List<Map<String, String>> linhas = new ArrayList<>();
            String linha;
            while ((linha = r.readLine()) != null) {
                if (linha.isBlank() || linha.startsWith("#")) {
                    continue;
                }
                String[] campos = linha.split(";", -1);
                if (cabecalho == null) {
                    cabecalho = campos;
                    continue;
                }
                Map<String, String> m = new LinkedHashMap<>();
                for (int i = 0; i < cabecalho.length; i++) {
                    m.put(cabecalho[i], i < campos.length ? campos[i].trim() : "");
                }
                linhas.add(m);
            }
            return linhas;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
