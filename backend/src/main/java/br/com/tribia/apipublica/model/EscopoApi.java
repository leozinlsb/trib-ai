package br.com.tribia.apipublica.model;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** O que uma chave de API pode fazer. Cada endpoint público exige o seu; a chave recebe só o necessário. */
public enum EscopoApi {
    /** Enviar mercadorias para análise (gera chamada à IA: consome cota). */
    ANALISES_CRIAR,
    /** Consultar status e resultado das análises da empresa e o consumo da própria chave. */
    ANALISES_LER;

    public static final Set<EscopoApi> PADRAO = EnumSet.allOf(EscopoApi.class);

    public static String juntar(Set<EscopoApi> escopos) {
        return escopos.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }

    /** @throws IllegalArgumentException com o escopo desconhecido */
    public static Set<EscopoApi> separar(String texto) {
        if (texto == null || texto.isBlank()) {
            return EnumSet.noneOf(EscopoApi.class);
        }
        return Arrays.stream(texto.split(","))
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .map(EscopoApi::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(EscopoApi.class)));
    }
}
