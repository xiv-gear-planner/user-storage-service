package app.xivgear.userstorage

import io.micronaut.runtime.Micronaut
import groovy.transform.CompileStatic
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityScheme
import org.slf4j.bridge.SLF4JBridgeHandler

import java.util.logging.Logger

@OpenAPIDefinition(
		info = @Info(
				title = 'xivgear-userdata-service',
				version = '0.0'
		)
)
@SecurityScheme(
		name = 'AntiCsrfHeaderAuth',
		type = SecuritySchemeType.APIKEY,
		in = SecuritySchemeIn.HEADER,
		paramName = 'xivgear-csrf',
		description = 'Anti-CSRF header, the correct value is "1"'
)
@CompileStatic
class Application {
	static void main(String[] args) {
		SLF4JBridgeHandler.removeHandlersForRootLogger()
		SLF4JBridgeHandler.install()
		Micronaut.build(args).with {
			it.args args
			packages 'app.xivgear.logging'
		}.start()
	}
}
