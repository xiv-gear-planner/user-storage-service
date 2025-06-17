package app.xivgear.userstorage.nosql

import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.oraclecloud.core.OracleCloudAuthConfigurationProperties
import jakarta.inject.Singleton
import oracle.nosql.driver.NoSQLHandle
import oracle.nosql.driver.NoSQLHandleConfig
import oracle.nosql.driver.NoSQLHandleFactory
import oracle.nosql.driver.iam.SignatureProvider
import oracle.nosql.driver.kv.StoreAccessTokenProvider

/**
 * Reads configuration and produces a NoSQLHandle.
 */
@Context
@Singleton
@CompileStatic
@Slf4j
@Factory
class OracleNoSqlConnector {

	private final URL endpoint

	OracleNoSqlConnector(@Value('${oracle-nosql.endpoint}') URL endpoint) {
		this.endpoint = endpoint
	}

	private NoSQLHandleConfig base() {
		NoSQLHandleConfig config = new NoSQLHandleConfig(endpoint)
		config.configureDefaultRetryHandler 8, 1_0000
		return config
	}

	@Singleton
	@Requires(property = 'oracle-nosql.mode', value = 'local')
	NoSQLHandle localNoSqlHandle() {
		NoSQLHandleConfig config = base()
		config.authorizationProvider = new StoreAccessTokenProvider()
		NoSQLHandle handle = NoSQLHandleFactory.createNoSQLHandle(config)
		return handle
	}

	@Singleton
	@Requires(property = 'oracle-nosql.mode', value = 'cloud')
	NoSQLHandle cloudNoSqlHandle(
			OracleCloudAuthConfigurationProperties auth,
			@Value('${oracle-nosql.compartment}') String compartmentId
	) {
		NoSQLHandleConfig config = base()
		SimpleAuthenticationDetailsProvider build = auth.builder.build()
		SignatureProvider sp = new SignatureProvider(build.tenantId, build.userId, build.fingerprint, new String(build.privateKey.readAllBytes()), null)
		config.authorizationProvider = sp
		config.defaultCompartment = compartmentId
		return NoSQLHandleFactory.createNoSQLHandle(config)
	}

}
