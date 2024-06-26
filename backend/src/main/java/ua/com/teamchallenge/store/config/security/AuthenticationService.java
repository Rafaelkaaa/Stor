package ua.com.teamchallenge.store.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ua.com.teamchallenge.store.api.dto.auth.RegisterDto;
import ua.com.teamchallenge.store.api.dto.response.auth.AuthDto;
import ua.com.teamchallenge.store.persistence.entity.token.Token;
import ua.com.teamchallenge.store.persistence.entity.user.User;
import ua.com.teamchallenge.store.persistence.entity.user.person.Person;
import ua.com.teamchallenge.store.persistence.repository.token.TokenRepository;
import ua.com.teamchallenge.store.persistence.repository.user.UserRepository;
import ua.com.teamchallenge.store.persistence.repository.user.person.PersonRepository;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final ReactiveAuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final PersonRepository personRepository;
    private final TokenRepository tokenRepository;
    private final UserRepository<User> userRepository;

    public Mono<AuthDto> login(RegisterDto registerDto) {
        return userRepository.findByLogin(registerDto.getLogin())
                .flatMap(user -> Mono.fromRunnable(() -> authenticationManager
                                .authenticate(new UsernamePasswordAuthenticationToken(registerDto.getLogin(),
                                        registerDto.getPassword())))
                        .then(Mono.defer(() -> {
                            Mono<String> jwtTokenMono = jwtService.generateToken(user);
                            Token token = new Token();
                            token.setUserId(user.getId());
                            return jwtTokenMono.flatMap(jwtToken -> {
                                token.setToken(jwtToken);
                                return tokenRepository.save(token)
                                        .thenReturn(new AuthDto(jwtToken));
                            });
                        })))
                .switchIfEmpty(Mono.error(new RuntimeException("User not found")));
    }

    public Mono<AuthDto> register(RegisterDto registerDto) {
        return userRepository.existsByLogin(registerDto.getLogin())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new RuntimeException("This email has already been used"));
                    } else {
                        return Mono.just(new Person())
                                .doOnNext(person -> {
                                    person.setLogin(registerDto.getLogin());
                                    person.setPassword(passwordEncoder.encode(registerDto.getPassword()));
                                })
                                .flatMap(person -> personRepository.save(person)
                                        .then(Mono.defer(() -> {
                                            Mono<String> jwtTokenMono = jwtService.generateToken(person);
                                            return jwtTokenMono.flatMap(jwtToken -> {
                                                Token token = new Token();
                                                token.setToken(jwtToken);
                                                token.setUserId(person.getId());
                                                return tokenRepository.save(token)
                                                        .thenReturn(new AuthDto(jwtToken));
                                            });
                                        })));
                    }
                });
    }
}
