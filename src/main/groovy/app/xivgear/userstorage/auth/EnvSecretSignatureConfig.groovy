package app.xivgear.userstorage.auth

import io.micronaut.context.annotation.Property
import io.micronaut.security.token.jwt.signature.secret.SecretSignatureConfiguration
import jakarta.inject.Named
import jakarta.inject.Singleton

//@Singleton
//@Named("verifier")
//class EnvSecretSignatureConfig extends SecretSignatureConfiguration {
//
//	EnvSecretSignatureConfig(@Property(name = 'auth.jwt-token') String secret) {
//		super("verifier")
//		this.secret = secret
//	}
//
//}
