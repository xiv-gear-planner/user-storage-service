package app.xivgear.userstorage.controllers

import app.xivgear.userstorage.nosql.RawNoSqlTable
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.BeanContext
import io.micronaut.context.annotation.Context
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.swagger.v3.oas.annotations.Operation
import jakarta.annotation.security.PermitAll
import jakarta.inject.Inject

@Slf4j
@Controller("/")
@CompileStatic
@Context
@PermitAll
class HealthReadyCheck {

	@Inject
	BeanContext ctx

	@SuppressWarnings(['GrMethodMayBeStatic'])
	@Operation(summary = "Health Check")
	@Get("/healthz")
	@Produces(MediaType.TEXT_PLAIN)
	String healthCheck() {
		return "Healthy"
	}

	@SuppressWarnings(['GrMethodMayBeStatic', 'unused'])
	@Operation(summary = "Ready Check")
	@Get("/readyz")
	@Produces(MediaType.TEXT_PLAIN)
	HttpResponse<String> readyCheck() {
		// TODO: this doesn't really work as intended, because the http server doesn't start until
		// all of the dependencies are already up
		var tables = ctx.getBeansOfType RawNoSqlTable
		tables.forEach {
			if (!it.initialized) {
				log.info "Table ${it.tableName} not initialized"
				return HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
						.body("Not Ready")
			}
		}
		return HttpResponse.ok("Ready")
	}
}
