package app.xivgear.userstorage.controllers

import app.xivgear.userstorage.dto.ValidationErrorResponse
import app.xivgear.userstorage.dto.ValidationErrorSingle
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Context
import io.micronaut.core.annotation.Order
import io.micronaut.core.order.Ordered
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.server.exceptions.ExceptionHandler
import jakarta.inject.Singleton
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Path

@Context
@Singleton
@Order(Ordered.HIGHEST_PRECEDENCE)
@CompileStatic
class ConstraintViolationExceptionHandler implements ExceptionHandler<ConstraintViolationException, HttpResponse<?>> {

	@Override
	HttpResponse<ValidationErrorResponse> handle(HttpRequest request, ConstraintViolationException exception) {
		List<ValidationErrorSingle> errors = []
		exception.constraintViolations.each {cv ->
			errors << new ValidationErrorSingle().tap {
				path = cv.propertyPath.toString()
				message = cv.message
				Path.Node lastNode = null
				for (Path.Node node : cv.propertyPath) {
					lastNode = node
				}
				field = lastNode.name
			}
		}
		var out = new ValidationErrorResponse()
		out.validationErrors = errors
		return HttpResponse.badRequest(out)
	}

}
