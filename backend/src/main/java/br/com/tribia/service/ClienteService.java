package br.com.tribia.service;

import br.com.tribia.dto.ClienteForm;
import br.com.tribia.exception.ApiException;
import br.com.tribia.exception.RecursoNaoEncontradoException;
import br.com.tribia.model.Cliente;
import br.com.tribia.repository.ClienteRepository;
import br.com.tribia.repository.NotaRepository;
import br.com.tribia.util.CnpjUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ClienteService {

    private final ClienteRepository repository;
    private final NotaRepository notas;

    public ClienteService(ClienteRepository repository, NotaRepository notas) {
        this.repository = repository;
        this.notas = notas;
    }

    public List<Cliente> listar() {
        return repository.findAll();
    }

    public Cliente buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente " + id + " não encontrado"));
    }

    @Transactional
    public Cliente criar(ClienteForm form) {
        String cnpj = cnpjValido(form.cnpj());
        if (repository.existsByCnpj(cnpj)) {
            throw conflito("Já existe uma empresa com o CNPJ " + CnpjUtil.formatar(cnpj) + ".");
        }
        return repository.save(Cliente.nova(cnpj, form.dados()));
    }

    /**
     * O CNPJ só pode mudar enquanto a empresa não tiver notas: é por ele que cada nota é reconhecida
     * como entrada ou saída da empresa.
     */
    @Transactional
    public Cliente atualizar(Long id, ClienteForm form) {
        Cliente c = buscar(id);
        String cnpj = cnpjValido(form.cnpj());
        if (!cnpj.equals(c.getCnpj())) {
            if (notas.existsByClienteId(id)) {
                throw conflito("O CNPJ não pode ser alterado porque a empresa já tem notas fiscais importadas.");
            }
            if (repository.existsByCnpj(cnpj)) {
                throw conflito("Já existe uma empresa com o CNPJ " + CnpjUtil.formatar(cnpj) + ".");
            }
            c.alterarCnpj(cnpj);
        }
        c.atualizar(form.dados());
        return c;
    }

    /** Exclusão lógica: notas e usuários são mantidos; a empresa pode ser reativada. */
    @Transactional
    public Cliente desativar(Long id) {
        Cliente c = buscar(id);
        c.desativar();
        return c;
    }

    @Transactional
    public Cliente reativar(Long id) {
        Cliente c = buscar(id);
        c.reativar();
        return c;
    }

    private static String cnpjValido(String cnpj) {
        String d = CnpjUtil.somenteDigitos(cnpj);
        if (!CnpjUtil.valido(d)) {
            throw ApiException.requisicaoInvalida("CNPJ inválido. Confira os 14 dígitos.");
        }
        return d;
    }

    private static ApiException conflito(String mensagem) {
        return new ApiException(HttpStatus.CONFLICT, "Conflito", mensagem);
    }
}
