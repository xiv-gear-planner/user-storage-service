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
		config.configureDefaultRetryHandler 8, 2_500
		return config
	}

	@Singleton
	@Requires(property = 'oracle-nosql.mode', value = 'local')
	NoSQLHandle localNoSqlHandle() {
		NoSQLHandleConfig config = base()
		config.authorizationProvider = new StoreAccessTokenProvider()
		NoSQLHandle handle = NoSQLHandleFactory.createNoSQLHandle config
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
		config.tap {
			authorizationProvider = sp
			defaultCompartment = compartmentId
			requestTimeout = 15_000
			configureDefaultRetryHandler 10, 2_000
			rateLimitingEnabled = true
			// Instead of limiting to 0.5 which would be the most obvious option for two instances, limit to higher.
			// This allows for us to actually hit the throttling sometimes which gets reported in the OCI dashboard.
			// The client-side rate limiting ('client' being the user data server) also seems to be a bit more
			// conservative than the 'true' rate limit (i.e. where you would get throttled).
			defaultRateLimitingPercentage = 80.0
		}
		return NoSQLHandleFactory.createNoSQLHandle(config)
	}

}
