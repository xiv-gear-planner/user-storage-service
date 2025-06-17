package app.xivgear.userstorage.controllers

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Context
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.core.order.Ordered
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponseFactory
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import jakarta.inject.Singleton
import org.reactivestreams.Publisher

@Filter("/userdata/**")
@Singleton
@CompileStatic
@Context
class AntiCsrfFilter implements Ordered, HttpServerFilter {

	@Override
	Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
		String value = request.headers.get 'xivgear-csrf'
		if (value == "1") {
			// Proceed
			return chain.proceed(request)
		}
		// Block if header missing
		return Publishers.just(HttpResponseFactory.INSTANCE.status(HttpStatus.FORBIDDEN))
	}

	final int order = -10
}
