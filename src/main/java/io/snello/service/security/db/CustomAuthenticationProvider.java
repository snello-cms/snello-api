package io.snello.service.security.db;

import io.micronaut.security.authentication.AuthenticationProvider;
import io.micronaut.security.authentication.AuthenticationRequest;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.UserDetails;
import io.reactivex.Flowable;
import io.snello.repository.JdbcRepository;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class CustomAuthenticationProvider
        implements AuthenticationProvider
{


    Logger logger = LoggerFactory.getLogger(CustomAuthenticationProvider.class);

    @Inject
    JdbcRepository jdbcRepository;


    @Override
    public Publisher<AuthenticationResponse> authenticate(AuthenticationRequest authenticationRequest) {
        logger.info("login for: " + authenticationRequest.getIdentity().toString());
        try {
            UserDetails user = jdbcRepository.login(authenticationRequest.getIdentity().toString(), authenticationRequest.getSecret().toString());
            if (user == null) {
                throw new Exception("user is null");
            }
            return Flowable.just(user);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return Flowable.just(null);
        }
    }

}
