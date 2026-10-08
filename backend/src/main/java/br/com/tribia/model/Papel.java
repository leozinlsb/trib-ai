package br.com.tribia.model;

/** Perfil de acesso de um {@link Usuario}. */
public enum Papel {
    /** Escritório: gerencia empresas e acessa todas. */
    ADMIN,
    /** Usuário de uma empresa cliente: acessa somente a própria empresa. */
    EMPRESA
}
