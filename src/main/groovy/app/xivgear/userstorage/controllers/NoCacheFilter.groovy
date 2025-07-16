package app.xivgear.userstorage.controllers

import groovy.transform.CompileStatic
import io.micronaut.core.order.Ordered
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.ResponseFilter
import io.micronaut.http.annotation.ServerFilter
import jakarta.inject.Singleton

@ServerFilter("/**")
@Singleton
@CompileStatic
class NoCacheFilter implements Ordered {

	@ResponseFilter
	void addCacheControlHeader(HttpRequest<?> request, MutableHttpResponse<?> response) {
		response.header("Cache-Control", "no-cache")
		response.header("Pragma", "no-cache")
		response.header("Expires", "0")
	}

	final int order = -20
}
