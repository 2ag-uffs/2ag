package dev.uffs.doisag.service;

import dev.uffs.doisag.repository.UsersRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

// essa classe serve só pro spring security
// é a ponte entre a segurança da nossa api e nosso banco de dados de usuários
@Service
public class AuthorizationService implements UserDetailsService {

    // o repository agora é final pra garantir que não vai ser alterado
    private final UsersRepository usersRepository;

    // este é o construtor que o spring usa para injetar o userRepository
    public AuthorizationService(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    // este é o método que o spring security chama de verdade na hora do login
    // ele recebe o email que o cara digitou e tem que devolver os dados do usuário
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // aqui ele usa o nosso repository para caçar o usuário pelo email
        var user = usersRepository.findByEmail(username);

        // se não achou ninguém com aquele email, a gente lança um erro que o spring entende
        if (user == null) {
            throw new UsernameNotFoundException("usuário não encontrado com o e-mail: " + username);
        }

        // se achou, retorna o usuário que o spring se vira com o resto (tipo, comparar a senha)
        return user;
    }
}