package app.xivgear.userstorage.nosql

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Context
import io.micronaut.core.annotation.Order
import io.micronaut.core.order.Ordered
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.server.exceptions.ExceptionHandler
import oracle.nosql.driver.RequestTimeoutException

@Context
@Order(Ordered.HIGHEST_PRECEDENCE)
@CompileStatic
class ThrottleExceptionHandler implements ExceptionHandler<RequestTimeoutException, HttpResponse<?>> {

	@Override
	HttpResponse<?> handle(HttpRequest request, RequestTimeoutException exception) {
		return HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS)
	}

}
